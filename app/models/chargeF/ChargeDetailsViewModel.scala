/*
 * Copyright 2019 HM Revenue & Customs
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

//package models.chargeF
//
//import play.api.libs.functional.syntax._
//import play.api.data.Form
//import play.api.libs.json.{OWrites, __}
//import uk.gov.hmrc.viewmodels.DateInput
//import ChargeDetails.formats
//
//case class ChargeDetailsViewModel(form: Form[ChargeDetails],
//                                  submitUrl: String,
//                                  returnUrl: String,
//                                  date: DateInput.ViewModel,
//                                  schemeName: String)
//
//object ChargeDetailsViewModel {
//  implicit lazy val writes: OWrites[ChargeDetailsViewModel] = (
//    (__ \ "form" ).read[Form[ChargeDetails]] and
//      (__ \ "submitUrl").read[String] and
//      (__ \ "returnUrl").read[String] and
//      (__ \ "date").read[DateInput.ViewModel] and
//      (__ \ "schemeName").read[String]
//    )(ChargeDetailsViewModel.apply _)
//
//}
