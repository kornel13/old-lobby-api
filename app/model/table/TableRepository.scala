package model.table

import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import slick.sql.SqlProfile.ColumnOption.SqlType

import scala.concurrent.{ExecutionContext, Future}

case class TableModelDb(primaryKey: Long, id: Long, name: String, participants: Int, sortingId: Long)

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

    def sortingId = column[Long]("SORTING_ID")

    def * = (primaryKey, id, name, participants, sortingId).mapTo[TableModelDb]

  }

  protected val tableQuery = TableQuery[TableDb]

  val emptyAction = DBIO.successful(-1)

  def findTableQuery(id: Long): Query[TableDb, TableModelDb, Seq] = tableQuery.filter(_.id === id)

  def createSchema: String = tableQuery.schema.createIfNotExistsStatements.mkString("\n")

  def dropSchema: String = tableQuery.schema.dropIfExistsStatements.mkString("\n")

  /**
    * I haven't got how to apply 'after_id' parameter if id is explicitly passed as an argument. Assumed solution
    * Since id is passed by a caller, afterId is used to determine order in table list -> internally sortingId
    * if after_id is negative the element is added in the beginning of the list (element calculated sorting_id is
    * decremented minimal existing one) otherwise it's added after last elemnt ( sorting_id calculated analogically)
    *
    */
  def add(table: TableModelDb, afterId: Long): Future[Int] = db.run {
    val sortingIfQuery = tableQuery.map(_.sortingId)
    val addingAction = for {
      sortingId <- if (afterId < 0) sortingIfQuery.min.result.map(_.map(_ - 1).getOrElse(table.id))
      else sortingIfQuery.max.result.map(_.map(_ + 1).getOrElse(table.id))
      addition <- tableQuery += table.copy(sortingId = sortingId)
    } yield addition
    val action = for {
      tableExists <- findTableQuery(table.id).exists.result
      result <- if (tableExists) emptyAction else addingAction
    } yield result
    action.transactionally
  }

  def remove(id: Long): Future[Int] = db.run(findTableQuery(id).delete)

  def update(table: TableModelDb): Future[Int] =
    db.run(findTableQuery(table.id).map(t => (t.name, t.participants)).update((table.name, table.participants)))

  def list: Future[Seq[TableModelDb]] = db.run(tableQuery.sortBy(_.sortingId).result)

}
