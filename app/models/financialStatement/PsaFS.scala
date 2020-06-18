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

package models.financialStatement

import java.time.LocalDate

import models.WithName
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath, Json, Reads}

case class PsaFS(chargeReference: String, chargeType: FSChargeType, dueDate: Option[LocalDate],
                 amountDue: BigDecimal, outstandingAmount: BigDecimal, stoodOverAmount: BigDecimal,
                 periodStartDate: LocalDate, periodEndDate: LocalDate, pstr: String)

object PsaFS {
  implicit val formats: Format[PsaFS] = Json.format[PsaFS]
}
