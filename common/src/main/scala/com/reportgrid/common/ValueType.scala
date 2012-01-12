/*
 *  ____    ____    _____    ____    ___     ____ 
 * |  _ \  |  _ \  | ____|  / ___|  / _/    / ___|        Precog (R)
 * | |_) | | |_) | |  _|   | |     | |  /| | |  _         Advanced Analytics Engine for NoSQL Data
 * |  __/  |  _ <  | |___  | |___  |/ _| | | |_| |        Copyright (C) 2010 - 2013 SlamData, Inc.
 * |_|     |_| \_\ |_____|  \____|   /__/   \____|        All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the 
 * GNU Affero General Public License as published by the Free Software Foundation, either version 
 * 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See 
 * the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this 
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.reportgrid.common

import blueeyes.json._
import blueeyes.json.JsonAST._
import blueeyes.json.xschema._
import blueeyes.json.xschema.Extractor._
import blueeyes.json.xschema.DefaultSerialization._

import scalaz._
import scalaz.Scalaz._

trait LengthEncoder {
  def fixedLength(d: BigInt): Option[Int]
  def fixedLength(s: String): Option[Int]
}

sealed trait ValueType

abstract class PrimitiveType extends ValueType

object ValueType extends PrimitiveTypeSerialization {
  case object Boolean extends PrimitiveType
 
  case object Long    extends PrimitiveType 
  case object Double  extends PrimitiveType
  case class  BigDecimal(width: Option[Int]) extends PrimitiveType
  
  case class  String(width: Option[Int]) extends PrimitiveType
  
  case object Null    extends PrimitiveType
  case object Nothing extends PrimitiveType
  
  case object Array extends ValueType
  case object Object extends ValueType

  def forValue(jvalue: JValue)(implicit lengthEncoder: LengthEncoder) : Option[PrimitiveType] = jvalue match {
    case JBool(_)        => Some(Boolean)
    case JInt(value)     => Some(BigDecimal(lengthEncoder.fixedLength(value)))
    case JDouble(_)      => Some(Double)
    case JString(value)  => Some(String(lengthEncoder.fixedLength(value)))
    case JNull           => Some(Null)
    case JNothing        => Some(Nothing)
    case _               => None
  }
}

trait SerializationHelpers {
  def fieldHasValue(field: JField): Boolean = field.value match {
    case JNull => false
    case _     => true
  }
}

trait PrimitiveTypeSerialization extends SerializationHelpers {
  import ValueType.{String => CTString, _}

  def nameOf(c: PrimitiveType): JValue = c match {
    case Long    => "Long"
    case Double  => "Double"
    case Boolean => "Boolean"
    case Null    => "Null"
    case Nothing => "Nothing"
    case BigDecimal(_) => "BigDecimal"
    case CTString(_)   => "String"
  }

  def widthOf(c : PrimitiveType) : JValue = c match {
    case BigDecimal(Some(width)) => JInt(width)
    case CTString(Some(width))   => JInt(width)
    case _                       => JNull
  }

  implicit val PrimtitiveTypeDecomposer : Decomposer[PrimitiveType] = new Decomposer[PrimitiveType] {
    def decompose(ctype : PrimitiveType) : JValue = JObject(
      List(JField("type", JString(nameOf(ctype))),
           JField("width", widthOf(ctype))).filter(fieldHasValue)
    )
  }

  implicit val PrimitiveTypeExtractor : Extractor[PrimitiveType] = new Extractor[PrimitiveType] with ValidatedExtraction[PrimitiveType] {
    override def validated(obj : JValue) : Validation[Error,PrimitiveType] = (
      (obj \ "type").validated[String] |@|
      (obj \ "width").validated[Option[Int]]).apply { (_,_) match {
        case ("Long",_) => Long
        case ("Double",_) => Double
        case ("Boolean",_) => Boolean
        case ("Null",_) => Null
        case ("Nothing",_) => Nothing
        case ("BigDecimal",width) => BigDecimal(width)
        case ("String",width) => CTString(width)
      }}
  }
}
