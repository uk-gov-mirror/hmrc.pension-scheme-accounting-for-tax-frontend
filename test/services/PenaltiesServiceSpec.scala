/*
 * Copyright 2021 HM Revenue & Customs
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

import java.time.LocalDate

import base.SpecBase
import config.FrontendAppConfig
import connectors.cache.FinancialInfoCacheConnector
import connectors.{FinancialStatementConnector, ListOfSchemesConnector}
import helpers.FormatHelper
import models.financialStatement.PsaFS
import models.financialStatement.PsaFSChargeType.{OTC_6_MONTH_LPP, AFT_INITIAL_LFP}
import models.{ListOfSchemes, ListSchemeDetails, PenaltySchemes}
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.Messages
import play.api.libs.json.{Json, JsObject}
import play.api.mvc.Results
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Value, Row}
import uk.gov.hmrc.viewmodels.Table.Cell
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import utils.DateHelper
import utils.DateHelper.dateFormatterDMY

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PenaltiesServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {

  import PenaltiesServiceSpec._

  private val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]
  private val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  private val mockFIConnector: FinancialInfoCacheConnector = mock[FinancialInfoCacheConnector]
  private val mockListOfSchemesConn: ListOfSchemesConnector = mock[ListOfSchemesConnector]
  private val penaltiesService = new PenaltiesService(mockAppConfig, mockFSConnector, mockFIConnector, mockListOfSchemesConn)

  def penaltyTables(statusClass: String, statusMessageKey: String, amountDue: String): Seq[JsObject] = Seq(
    Json.obj(
      "header" -> msg"penalties.period".withArgs("1 April", "30 June 2020"),
      "penaltyTable" -> Table(head = head, rows = rows(aftLink("2020-04-01"), statusClass, statusMessageKey, amountDue),attributes = Map("role" -> "table"),classes= Seq("hmrc-responsive-table"))
    ),
    Json.obj(
      "header" -> msg"penalties.period".withArgs("1 July", "30 September 2020"),
      "penaltyTable" -> Table(head = head, rows = rows(otcLink("2020-07-01"), statusClass, statusMessageKey, amountDue),attributes = Map("role" -> "table"),classes= Seq("hmrc-responsive-table"))
    ),
    Json.obj(
      "header" -> msg"penalties.period".withArgs("1 October", "31 December 2020"),
      "penaltyTable" -> Table(head = head, rows = rows(otcLink("2020-10-01"), statusClass, statusMessageKey, amountDue),attributes = Map("role" -> "table"),classes= Seq("hmrc-responsive-table"))
    )
  )

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockAppConfig.minimumYear).thenReturn(year)
    when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(psaFSResponse()))))
    DateHelper.setDate(Some(dateNow))
  }

  "getPsaFsJson" must {
    "return the penalty tables based on API response for paymentOverdue" in {
      whenReady(penaltiesService.getPsaFsJson(psaFSResponse(amountDue = 1029.05, dueDate = LocalDate.parse("2020-07-15")), srn, year)) {
        _.mustBe(
          penaltyTables(
            statusClass = "govuk-tag govuk-tag--red",
            statusMessageKey = "penalties.status.paymentOverdue",
            amountDue = "1,029.05"
          )
        )
      }
    }
    "return the penalty tables based on API response for noPaymentDue" in {
      whenReady(penaltiesService.getPsaFsJson(psaFSResponse(amountDue = 0.00, dueDate = LocalDate.parse("2020-07-15")), srn, year)) {
        _.mustBe(
          penaltyTables(
            statusClass = "govuk-visually-hidden",
            statusMessageKey = "penalties.status.visuallyHiddenText.noPaymentDue",
            amountDue = "0.00"
          )
        )
      }
    }
    "return the penalty tables based on API response for paymentIsDue" in {
      whenReady(penaltiesService.getPsaFsJson(psaFSResponse(amountDue = 5.00, dueDate = LocalDate.now()), srn, year)) {
        _.mustBe(
          penaltyTables(
            statusClass = "govuk-visually-hidden",
            statusMessageKey = "penalties.status.visuallyHiddenText.paymentIsDue",
            amountDue = "5.00"
          )
        )
      }
    }
  }

  "isPaymentOverdue" must {
    "return true if amountDue is greater than 0 and due date is after today" in {
      penaltiesService.isPaymentOverdue(psaFS(BigDecimal(0.01), Some(dateNow.minusDays(1)))) mustBe true
    }

    "return false if amountDue is less than or equal to 0" in {
      penaltiesService.isPaymentOverdue(psaFS(BigDecimal(0.00), Some(dateNow.minusDays(1)))) mustBe false
    }

    "return false if amountDue is greater than 0 and due date is not defined" in {
      penaltiesService.isPaymentOverdue(psaFS(BigDecimal(0.01), None)) mustBe false
    }

    "return false if amountDue is greater than 0 and due date is today" in {
      penaltiesService.isPaymentOverdue(psaFS(BigDecimal(0.01), Some(dateNow))) mustBe false
    }
  }

  "chargeDetailsRows" must {
    "return the correct rows when all rows need to be displayed" in {
      val rows = Seq(totalAmount(), paymentAmount(), reviewAmount(), totalDueAmount(date = "15 July 2020"))
      penaltiesService.chargeDetailsRows(psaFSResponse(amountDue = 1029.05, dueDate = LocalDate.parse("2020-07-15")).head) mustBe rows
    }

    "return the correct rows when payment row need not be displayed" in {
      val rows = Seq(totalAmount(), reviewAmount(), totalDueAmount())
      val psaData = psaFS(outStandingAmount = BigDecimal(80000.00))
      penaltiesService.chargeDetailsRows(psaData) mustBe rows
    }

    "return the correct rows when amountUnderReview row need not be displayed" in {
      val rows = Seq(totalAmount(), paymentAmount(BigDecimal(78970.95)), totalDueAmount())
      val psaData = psaFS(stoodOverAmount = BigDecimal(0.00))
      penaltiesService.chargeDetailsRows(psaData) mustBe rows
    }

    "return the correct rows when totalDue row need is displayed without date" in {
      val rows = Seq(totalAmount(), paymentAmount(), reviewAmount(), totalDueAmountWithoutDate())
      val psaData = psaFS(dueDate = None)
      penaltiesService.chargeDetailsRows(psaData) mustBe rows
    }
  }

  "penaltySchemes" must {
    "return a combination of all associated and unassociated schemes returned in correct format" in {
      when(mockFSConnector.getPsaFS(any())(any(), any())).thenReturn(Future.successful(psaFSResponse()))
      when(mockFIConnector.save(any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockListOfSchemesConn.getListOfSchemes(any())(any(), any())).thenReturn(Future.successful(Right(listOfSchemes)))

      whenReady(penaltiesService.penaltySchemes("2020", "PsaID")(implicitly, implicitly)) {
        _ mustBe penaltySchemes
      }
    }
  }

}

object PenaltiesServiceSpec {

  val year: Int = 2020
  val srn: String = "S2400000041"
  val dateNow: LocalDate = LocalDate.now()

  def psaFSResponse(amountDue: BigDecimal = BigDecimal(0.01), dueDate: LocalDate = dateNow): Seq[PsaFS] = Seq(
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = AFT_INITIAL_LFP,
      dueDate = Some(dueDate),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = amountDue,
      periodStartDate = LocalDate.parse("2020-04-01"),
      periodEndDate = LocalDate.parse("2020-06-30"),
      pstr = "24000040IN"
    ),
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(dueDate),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = amountDue,
      periodStartDate = LocalDate.parse("2020-07-01"),
      periodEndDate = LocalDate.parse("2020-09-30"),
      pstr = "24000041IN"
    ),
    PsaFS(
      chargeReference = "XY002610150184",
      chargeType = OTC_6_MONTH_LPP,
      dueDate = Some(dueDate),
      totalAmount = 80000.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = amountDue,
      periodStartDate = LocalDate.parse("2020-10-01"),
      periodEndDate = LocalDate.parse("2020-12-31"),
      pstr = "24000041IN"
    )
  )

  def psaFS(
             amountDue: BigDecimal = BigDecimal(1029.05),
             dueDate: Option[LocalDate] = Some(dateNow),
             totalAmount: BigDecimal = BigDecimal(80000.00),
             outStandingAmount: BigDecimal = BigDecimal(56049.08),
             stoodOverAmount: BigDecimal = BigDecimal(25089.08)
           ): PsaFS =
    PsaFS("XY002610150184", AFT_INITIAL_LFP, dueDate, totalAmount, amountDue, outStandingAmount, stoodOverAmount, dateNow, dateNow, pstr)

  val pstr: String = "24000040IN"
  val zeroAmount: BigDecimal = BigDecimal(0.00)
  val formattedDateNow: String = dateNow.format(dateFormatterDMY)

  private def head(implicit messages: Messages) = Seq(
    Cell(msg"penalties.column.penalty"),
    Cell(msg"penalties.column.amount"),
    Cell(msg"penalties.column.chargeReference"),
    Cell(Html(s"<span class='govuk-visually-hidden'>${messages("penalties.column.paymentStatus")}</span>"))
  )

  private def rows(link: String,
                   statusClass: String,
                   statusMessageKey: String,
                   amountDue: String
                  )(implicit messages: Messages) = Seq(Seq(
    Cell(Html(s"""<span class=hmrc-responsive-table__heading aria-hidden=true>${messages("penalties.column.penalty")}</span>${link}""")),
    Cell(Html(s"""<span class=hmrc-responsive-table__heading aria-hidden=true>${messages("penalties.column.amount")}</span>£${amountDue}""")),
    Cell(Html(s"""<span class=hmrc-responsive-table__heading aria-hidden=true>${messages("penalties.column.chargeReference")}</span>${"XY002610150184"}""")),
    Cell(Html(s"<span class='$statusClass'>${messages(statusMessageKey)}</span>"))
  ))

  def aftLink(startDate: String): String =
    s"<a id=XY002610150184 class=govuk-link " +
      s"href=${controllers.financialStatement.penalties.routes.ChargeDetailsController.onPageLoad(srn, startDate, "0").url}>" +
      s"Accounting for Tax late filing penalty<span class=govuk-visually-hidden>for charge reference XY002610150184</span> </a>"

  def otcLink(startDate: String): String =
    s"<a id=XY002610150184 class=govuk-link " +
      s"href=${controllers.financialStatement.penalties.routes.ChargeDetailsController.onPageLoad(srn, startDate, "0").url}>" +
      s"Overseas transfer charge late payment penalty (6 months)<span class=govuk-visually-hidden>for charge reference XY002610150184</span> </a>"


  def totalAmount(amount: BigDecimal = BigDecimal(80000.00)): Row = Row(
    key = Key(Literal("Accounting for Tax late filing penalty"), classes = Seq("govuk-!-width-three-quarters")),
    value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
  )

  def paymentAmount(amount: BigDecimal = BigDecimal(53881.87)): Row = Row(
    key = Key(msg"penalties.chargeDetails.payments", classes = Seq("govuk-!-width-three-quarters")),
    value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
  )

  def reviewAmount(amount: BigDecimal = BigDecimal(25089.08)): Row = Row(
    key = Key(msg"penalties.chargeDetails.amountUnderReview", classes = Seq("govuk-!-width-three-quarters")),
    value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
  )

  def totalDueAmount(amount: BigDecimal = BigDecimal(1029.05), date: String = formattedDateNow): Row = Row(
    key = Key(msg"penalties.chargeDetails.totalDueBy".withArgs(date), classes = Seq("govuk-table__header--numeric", "govuk-!-padding-right-0")),
    value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"), classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
  )

  def totalDueAmountWithoutDate(amount: BigDecimal = BigDecimal(1029.05)): Row = Row(
    key = Key(msg"penalties.chargeDetails.totalDue", classes = Seq("govuk-table__header--numeric", "govuk-!-padding-right-0")),
    value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"),
      classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
  )

  val penaltySchemes: Seq[PenaltySchemes] = Seq(
    PenaltySchemes(Some("Assoc scheme"), "24000040IN", Some("SRN123")),
    PenaltySchemes(None, "24000041IN", None),
    PenaltySchemes(None, "24000041IN", None)
  )

  val listOfSchemes: ListOfSchemes = ListOfSchemes("", "", Some(List(
    ListSchemeDetails("Assoc scheme", "SRN123", "", None, Some("24000040IN"), None, None))))

}
