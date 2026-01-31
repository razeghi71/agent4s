package no.marz.agent4s.llm.model

sealed trait Role

object Role:
  case object System extends Role
  case object User extends Role
  case object Assistant extends Role
  case object Tool extends Role
