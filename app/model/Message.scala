package model
import julienrf.json.derived.flat
import model.table.TableModel
import play.api.libs.json.Format
import play.api.libs.json._

sealed trait Message
object Message {
  implicit val format: Format[Message] = flat.oformat[Message]((__ \ "$type").format[String])
}

//Authentication messages
final case class login(username: String, password: String) extends Message
case object login_failed extends Message
case object already_logged extends Message
final case class login_successful(user_type: String) extends Message

//Pinging messages
final case class ping(seq: Long) extends Message
final case class pong(seq: Long) extends Message

//Subscription messages
case object subscribe_tables extends Message
case object unsubscribe_tables extends Message
case object already_subscribed extends Message
case object already_unsubscribed extends Message

//Update messages
final case class table_list(tables: Seq[TableModel]) extends Message
final case class table_added(after_id: Long, table: TableModel) extends Message
final case class table_updated(table: TableModel) extends Message
final case class table_removed(id: Long) extends Message

//Privileged commands
case object not_authorized extends Message
final case class add_table(after_id: Long, table: TableModel) extends Message
final case class update_table(table: TableModel) extends Message
final case class remove_table(id: Long) extends Message

final case class removal_failed(id: Long) extends Message
final case class update_failed(id: Long) extends Message