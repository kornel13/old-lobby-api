package model.table

import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import slick.sql.SqlProfile.ColumnOption.SqlType

import scala.concurrent.{ExecutionContext, Future}

case class TableModelDb(primaryKey: Long, id: Long, name: String, participants: Int)

@Singleton
class TableRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ex: ExecutionContext) {
  protected val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  protected class TableDb(tag: Tag) extends Table[TableModelDb](tag, "TABLE") {
    def primaryKey = column[Long]("PRIMARY_KEY", SqlType("SERIAL"), O.PrimaryKey, O.AutoInc)

    def id = column[Long]("ID")

    def name = column[String]("NAME")

    def participants = column[Int]("PARTICIPANTS")

    def * = (primaryKey, id, name, participants).mapTo[TableModelDb]

  }

  protected val tableQuery = TableQuery[TableDb]

  val emptyAction = DBIO.successful(-1)

  def findTableQuery(id: Long): Query[TableDb, TableModelDb, Seq] = tableQuery.filter(_.id === id)

  def createSchema: String = tableQuery.schema.createIfNotExistsStatements.mkString("\n")

  def dropSchema: String = tableQuery.schema.dropIfExistsStatements.mkString("\n")

  def add(table: TableModelDb): Future[Int] = db.run {
    val action = for {
      tableExists <- findTableQuery(table.id).exists.result
      result <- if (tableExists) emptyAction else tableQuery += table
    } yield result
    action.transactionally
    tableQuery += table
  }

  def remove(id: Long): Future[Int] = db.run(findTableQuery(id).delete)

  def update(table: TableModelDb): Future[Int] = db.run(findTableQuery(table.id).update(table))

  def list: Future[Seq[TableModelDb]] = db.run(tableQuery.result)

}
