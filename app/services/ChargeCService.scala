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

package services

import controllers.chargeB.{routes => _}
import models.{Employer, UserAnswers}
import pages.chargeC.{ChargeCDetailsPage, IsSponsoringEmployerIndividualPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage}
import play.api.i18n.Messages
import play.api.libs.json.JsArray
import play.api.libs.json.Reads._
import play.api.mvc.Call
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import utils.CheckYourAnswersHelper.formatBigDecimalAsString
import viewmodels.Table
import viewmodels.Table.Cell
import java.time.LocalDate
import models.LocalDateBinder._

object ChargeCService {

  def getSponsoringEmployersIncludingDeleted(ua: UserAnswers, srn: String, startDate: LocalDate): Seq[Employer] = {
    def numberOfEmployersIncludingDeleted:Int = (ua.data \ "chargeCDetails" \ "employers")
      .toOption.map(_.as[JsArray].value.length)
      .getOrElse(0)

    def getEmployerDetails(index: Int): Option[(String, Boolean)] = ua.get(IsSponsoringEmployerIndividualPage(index)) flatMap {
        case true => ua.get(SponsoringIndividualDetailsPage(index)).map(i => Tuple2(i.fullName, i.isDeleted))
        case _ => ua.get(SponsoringOrganisationDetailsPage(index)).map(o => Tuple2(o.name, o.isDeleted))
      }

    (0 until numberOfEmployersIncludingDeleted).flatMap { index =>
      getEmployerDetails(index).flatMap { case (name, isDeleted) =>
        ua.get(ChargeCDetailsPage(index)).map{ chargeDetails =>
          Employer(
            index,
            name,
            chargeDetails.amountTaxDue,
            viewUrl(index, srn, startDate).url,
            removeUrl(index, srn, startDate).url,
            isDeleted
          )
        }
      }.toSeq
    }
  }

  def getSponsoringEmployers(ua: UserAnswers, srn: String, startDate: LocalDate): Seq[Employer] =
    getSponsoringEmployersIncludingDeleted(ua, srn, startDate).filterNot(_.isDeleted)

  def viewUrl(index: Int, srn: String, startDate: LocalDate): Call = controllers.chargeC.routes.CheckYourAnswersController.onPageLoad(srn, startDate, index)

  def removeUrl(index: Int, srn: String, startDate: LocalDate): Call = controllers.chargeC.routes.DeleteEmployerController.onPageLoad(srn, startDate, index)

  def mapToTable(members: Seq[Employer], canChange: Boolean)(implicit messages: Messages): Table = {
    val head = Seq(
      Cell(msg"addEmployers.employer.header", classes = Seq("govuk-!-width-one-half")),
      Cell(msg"addEmployers.amount.header", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"")
    ) ++ (if(canChange) Seq(Cell(msg"")) else Nil)

    val rows = members.map { data =>
      Seq(
        Cell(Literal(data.name), classes = Seq("govuk-!-width-one-half")),
        Cell(Literal(s"£${formatBigDecimalAsString(data.amount)}"), classes = Seq("govuk-!-width-one-quarter")),
        Cell(link(data.viewLinkId, "site.view", data.viewLink, data.name), classes = Seq("govuk-!-width-one-quarter"))
      ) ++ (if(canChange) Seq(Cell(link(data.removeLinkId, "site.remove", data.removeLink, data.name), classes = Seq("govuk-!-width-one-quarter"))) else Nil)
    }
    val totalAmount = members.map(_.amount).sum

    val totalRow = Seq(Seq(
      Cell(msg"addMembers.total", classes = Seq("govuk-table__header--numeric")),
      Cell(Literal(s"£${formatBigDecimalAsString(totalAmount)}"), classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"")
    ) ++ (if(canChange) Seq(Cell(msg"")) else Nil) )

    Table(head = head, rows = rows ++ totalRow)
  }

  def link(id: String, text: String, url: String, name: String)(implicit messages: Messages): Html = {
    val hiddenTag = "govuk-visually-hidden"
    Html(s"<a id=$id href=$url> ${messages(text)}" +
      s"<span class= $hiddenTag>${messages(s"chargeC.addEmployers.visuallyHidden", name)}</span> </a>")
  }

}
