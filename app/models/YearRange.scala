/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models

import java.time.{LocalDate, Year}

import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json._
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

sealed trait YearRange {
  def label: Text.Message
}

case class DynamicYearRange(startYear: String) extends YearRange {
  override def toString: String = startYear
  override def label: Text.Message = msg"yearRangeRadio".withArgs(toString, (toString.toInt + 1).toString)
}

object DynamicYearRange {
  implicit val writes: Writes[DynamicYearRange] = new Writes[DynamicYearRange] {
    def writes(yr: DynamicYearRange): JsValue = JsString(yr.toString)
  }
}

object YearRange extends Enumerable.Implicits {
  def currentYear = new DynamicYearRange(Year.now.toString)

  def values: Seq[DynamicYearRange] = {
    val maxYear = if (LocalDate.now.getMonthValue > 3) Year.now.getValue + 1 else Year.now.getValue
    (2019 to maxYear).reverse.map( year => DynamicYearRange(year.toString) )
  }

  def getLabel(yearRange: YearRange)(implicit messages: Messages): Literal = {
    values.find(_.toString == yearRange.toString) match {
      case Some(yr) => Literal(yr.label.resolve)
      case _ => throw new RuntimeException("Unknown year range: " + yearRange.toString )
    }
  }

  def radios(form: Form[_])(implicit messages: Messages): Seq[Radios.Item] =
    Radios(form("value"), values.map(yearRange => Radios.Radio(getLabel(yearRange), yearRange.toString)))

  implicit def enumerable: Enumerable[YearRange] = Enumerable(values.map(yearRange => yearRange.toString -> yearRange): _*)
}
