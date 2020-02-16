package model.table

import io.scalaland.chimney.dsl._
import play.api.libs.json.{Format, Json}

case class TableModel(id: Long, name: String, participants: Int)
object TableModel {
  implicit val format: Format[TableModel] = Json.format
}

object TableModelMapper {
  def toDb(model: TableModel): TableModelDb =
    model.into[TableModelDb].withFieldConst(_.primaryKey, 0L).transform

  def toMsg(model: TableModelDb): TableModel = model.transformInto[TableModel]
}
