package model.user

import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

case class UserModelDb(userName: String, passwordHash: String, role: String, subscription: Boolean)

@Singleton
class UserRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ex: ExecutionContext) {
  protected val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  protected class UserDb(tag: Tag) extends Table[UserModelDb](tag, "USERS") {
    def userName = column[String]("USER_NAME", O.PrimaryKey)

    def passwordHash = column[String]("PASS_HASH")

    def role = column[String]("ROLE")

    def subscription = column[Boolean]("SUBSCRIPTION")

    def * = (userName, passwordHash, role, subscription).mapTo[UserModelDb]

  }

  protected val tableQuery = TableQuery[UserDb]

  def createSchema: String = tableQuery.schema.createIfNotExistsStatements.mkString("\n")

  def dropSchema: String = tableQuery.schema.dropIfExistsStatements.mkString("\n")

  def findUserQuery(userName: String): Query[UserDb, UserModelDb, Seq] = tableQuery.filter(_.userName === userName)

  val emptyAction = DBIO.successful(-1)

  def getUser(userName: String): Future[Option[UserModelDb]] = db.run(findUserQuery(userName).result.headOption)

  def subscribe(userName: String, subscription: Boolean): Future[Int] =
    db.run(findUserQuery(userName).map(_.subscription).update(subscription))

  def listUsers: Future[Seq[UserModelDb]] = db.run(tableQuery.result)

  def addUser(user: UserModelDb): Future[Int] = db.run {
    val action = for {
      userExists <- findUserQuery(user.userName).exists.result
      result <- if (userExists) emptyAction else tableQuery += user
    } yield result
    action.transactionally
  }

  def removeUser(userName: String): Future[Int] = db.run(findUserQuery(userName).delete)

}
