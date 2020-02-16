package model

import javax.inject.{Inject, Singleton}
import model.table.TableRepository
import model.user.UserRepository

@Singleton
class EvolutionRepository @Inject()(userRepository: UserRepository, tableRepository: TableRepository) {

  def getEvolutionSchema: String = schemaEvolutionTemplate(
    create = Seq(userRepository.createSchema, tableRepository.createSchema),
    drop = Seq(userRepository.dropSchema, tableRepository.dropSchema)
  )

  private def schemaEvolutionTemplate(create: Seq[String], drop: Seq[String]) =
    s"""
       |# --- !Ups
       |
       |${create.mkString(";\n")}
       |
       |# --- !Downs
       |
       |${drop.mkString(";\n")}
       |
       |""".stripMargin
}
