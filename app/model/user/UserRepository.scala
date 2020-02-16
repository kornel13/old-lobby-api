package model.user

import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

case class UserModelDb(userName: String, passwordHash: String, role: String)

@Singleton
class UserRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ex: ExecutionContext) {
  protected val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  protected class UserDb(tag: Tag) extends Table[UserModelDb](tag, "USERS") {
    def userName = column[String]("USER_NAME", O.PrimaryKey)

    def passwordHash = column[String]("PASS_HASH")

    def role = column[String]("ROLE")

    def * = (userName, passwordHash, role).mapTo[UserModelDb]

  }

  protected val tableQuery = TableQuery[UserDb]

  def createSchema: String = tableQuery.schema.createIfNotExistsStatements.mkString("\n")
  def dropSchema: String = tableQuery.schema.dropIfExistsStatements.mkString("\n")

  def getUser(userName: String): Future[Option[UserModelDb]] = db.run(tableQuery.result.headOption)

  def listUsers: Future[Seq[UserModelDb]] = db.run(tableQuery.result)

  def addUser(user: UserModelDb):Future[Int] = db.run(tableQuery += user)

  def removeUser(username: String): Future[Int] = db.run(tableQuery.filter(_.userName === username).delete)

}
