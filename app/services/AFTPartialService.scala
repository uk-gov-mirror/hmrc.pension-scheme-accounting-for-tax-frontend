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

import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import helpers.FormatHelper
import models.financialStatement.{PsaFS, SchemeFS}
import models.{AFTOverview, Draft, LockDetail, Quarters}
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.viewmodels._
import utils.DateHelper
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels.{AFTViewModel, DashboardAftViewModel, Link}

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import models.financialStatement.PaymentOrChargeType.{getPaymentOrChargeType, AccountingForTaxCharges}

class AFTPartialService @Inject()(
                                   appConfig: FrontendAppConfig,
                                   paymentsAndChargesService: PaymentsAndChargesService,
                                   aftConnector: AFTConnector,
                                   aftCacheConnector: UserAnswersCacheConnector
                                 )(implicit ec: ExecutionContext) {

  def retrievePspDashboardAftReturnsModel(srn: String, pstr: String, authorisingPsaId: String)
                                         (implicit hc: HeaderCarrier, messages: Messages): Future[DashboardAftViewModel] =
      for {
        overview <- aftConnector.getAftOverview(pstr)
        inProgressReturnsLinkOpt <- pspAftDashboardGetInProgressReturnsModel(overview, srn, pstr)
        inProgressReturns = overview.filter(_.compiledVersionAvailable)
        subHeading <- optionSubHeading(inProgressReturns, pstr, srn, authorisingPsaId)
      } yield {

        val links: Seq[Link] = Seq(
            inProgressReturnsLinkOpt,
            Option(Link("aftLoginLink", appConfig.aftLoginUrl.format(srn), msg"aftPartial.start.link")),
            getPastReturnsModelOpt(overview, srn).map(_.link)
          ).flatten

        DashboardAftViewModel(subHeading, links)
      }



  // scalastyle:off method.length
  def retrievePspDashboardUpcomingAftChargesModel(schemeFs: Seq[SchemeFS], srn: String)
                                                 (implicit messages: Messages): DashboardAftViewModel = {

    val upcomingCharges: Seq[SchemeFS] =
      paymentsAndChargesService.extractUpcomingCharges(schemeFs)

    val pastCharges: Seq[SchemeFS] = schemeFs.filter(_.periodEndDate.isBefore(DateHelper.today))

    val total = upcomingCharges.map(_.amountDue).sum

    val span =
      if (upcomingCharges.map(_.dueDate).distinct.size == 1) {
        msg"pspDashboardUpcomingAftChargesCard.span.singleDueDate"
          .withArgs(upcomingCharges.map(_.dueDate).distinct.flatten.head.format(DateTimeFormatter.ofPattern("d MMMM yyyy")))
      } else {
        msg"pspDashboardUpcomingAftChargesCard.span.multipleDueDate"
      }

    val subHeading = Json.obj(
      "total" -> s"${FormatHelper.formatCurrencyAmountAsString(total)}",
      "span" -> span
    )


    val viewUpcomingLink: Option[Link] = {
      if (upcomingCharges == Seq.empty) {
        None
      } else {
        val nonAftUpcomingCharges: Seq[SchemeFS] = upcomingCharges.filter(p => getPaymentOrChargeType(p.chargeType) != AccountingForTaxCharges)
        val linkText: Text = if (upcomingCharges.map(_.dueDate).distinct.size == 1 && nonAftUpcomingCharges.isEmpty) {
           msg"pspDashboardUpcomingAftChargesCard.link.paymentsAndChargesForPeriod.single"
              .withArgs(
                startDate(upcomingCharges).format(DateTimeFormatter.ofPattern("d MMMM")),
                endDate(upcomingCharges).format(DateTimeFormatter.ofPattern("d MMMM"))
              )
          } else {
            msg"pspDashboardUpcomingAftChargesCard.link.paymentsAndChargesForPeriod.multiple"
          }
        Some(Link("upcoming-payments-and-charges", appConfig.upcomingChargesUrl.format(srn), linkText, None))
      }
    }

    val viewPastPaymentsAndChargesLink: Option[Link] =
      if (pastCharges == Seq.empty) {
        None
      } else {
        Some(Link(
          id = "past-payments-and-charges",
          url = appConfig.paymentsAndChargesUrl.format(srn),
          linkText = msg"pspDashboardUpcomingAftChargesCard.link.allPaymentsAndCharges",
          hiddenText = None
        ))
      }


    val links = Seq(viewUpcomingLink, viewPastPaymentsAndChargesLink).flatten

    DashboardAftViewModel(
      subHeadings = Seq(subHeading),
      links = links
    )
  }

  // scalastyle:off method.length
  def retrievePspDashboardOverdueAftChargesModel(schemeFs: Seq[SchemeFS], srn: String)
                                                (implicit messages: Messages): DashboardAftViewModel = {

    val totalOverdue: BigDecimal = schemeFs.map(_.amountDue).sum
    val totalInterestAccruing: BigDecimal = schemeFs.map(_.accruedInterestTotal).sum

    val subHeadingTotal = Json.obj(
      "total" -> s"${FormatHelper.formatCurrencyAmountAsString(totalOverdue)}",
      "span" -> msg"pspDashboardOverdueAftChargesCard.total.span"
    )

    val subHeadingInterestAccruing = Json.obj(
      "total" -> s"${FormatHelper.formatCurrencyAmountAsString(totalInterestAccruing)}",
      "span" -> msg"pspDashboardOverdueAftChargesCard.interestAccruing.span"
    )


    val viewOverdueLink: Option[Link] = {
      if (schemeFs == Seq.empty) {
        None
      } else {
        val nonAftOverdueCharges: Seq[SchemeFS] = schemeFs.filter(p => getPaymentOrChargeType(p.chargeType) != AccountingForTaxCharges)
        val linkText: Text = if (schemeFs.map(_.periodStartDate).distinct.size == 1 && nonAftOverdueCharges.isEmpty) {
            msg"pspDashboardOverdueAftChargesCard.viewOverduePayments.link.singlePeriod"
              .withArgs(
                startDate(schemeFs).format(DateTimeFormatter.ofPattern("d MMMM")),
                endDate(schemeFs).format(DateTimeFormatter.ofPattern("d MMMM"))
              )
          } else {
            msg"pspDashboardOverdueAftChargesCard.viewOverduePayments.link.multiplePeriods"
          }
        Some(Link("overdue-payments-and-charges", appConfig.overdueChargesUrl.format(srn), linkText, None))
      }
    }

    DashboardAftViewModel(
      subHeadings = Seq(subHeadingTotal, subHeadingInterestAccruing),
      links = Seq(viewOverdueLink).flatten
    )
  }

  val startDate: Seq[SchemeFS] => LocalDate = schemeFs => schemeFs.map(_.periodStartDate).distinct.head
  val endDate: Seq[SchemeFS] => LocalDate = schemeFs => schemeFs.map(_.periodEndDate).distinct.head

  private def optionSubHeading(
                                inProgressReturns: Seq[AFTOverview],
                                schemePstr: String,
                                srn: String,
                                authorisingPsaId: String
                              )(
                                implicit hc: HeaderCarrier,
                                messages: Messages
                              ): Future[Seq[JsObject]] = {
    if (inProgressReturns.size == 1) {
      val startDate = inProgressReturns.head.periodStartDate.toString

      aftCacheConnector.lockDetail(srn, startDate) flatMap {
        optLockDetail =>
          if (inProgressReturns.head.numberOfVersions == 1) {
            aftConnector.getIsAftNonZero(schemePstr, startDate, aftVersion = "1") flatMap {
              case true =>
                Future.successful(Seq(singleReturnSubHeading(inProgressReturns, optLockDetail, authorisingPsaId)))
              case _ =>
                Future.successful(Seq.empty)
            }
          } else {
            Future.successful(Seq(singleReturnSubHeading(inProgressReturns, optLockDetail, authorisingPsaId)))
          }
      }
    } else if (inProgressReturns.size > 1) {
      Future.successful(Seq(multipleReturnSubHeading(inProgressReturns)))
    } else {
      Future.successful(Seq.empty)
    }
  }

  private def multipleReturnSubHeading(inProgressReturns: Seq[AFTOverview])
                                      (implicit messages: Messages): JsObject =
    Json.obj(
      "h3" -> msg"pspDashboardAftReturnsCard.h3.multiple".withArgs(inProgressReturns.size.toString).resolve,
      "span" -> msg"pspDashboardAftReturnsCard.span.multiple".resolve
    )

  private def singleReturnSubHeading(inProgressReturns: Seq[AFTOverview], lockDetail: Option[LockDetail], authorisingPsaId: String)
                                    (implicit messages: Messages): JsObject = {

    val startDate: LocalDate = inProgressReturns.head.periodStartDate
    val startDateStr: String = startDate.format(DateTimeFormatter.ofPattern("d MMMM"))
    val endDate: String = Quarters.getQuarter(startDate).endDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))

    val h3: String =
      if (lockDetail.nonEmpty) {
        if (lockDetail.get.psaOrPspId == authorisingPsaId) {
          msg"pspDashboardAftReturnsCard.h3.single.lockedBy".withArgs(lockDetail.get.name).resolve
        } else {
          msg"pspDashboardAftReturnsCard.h3.single.locked".resolve
        }
      } else {
        msg"pspDashboardAftReturnsCard.h3.single".resolve
      }

    Json.obj(
      "h3" -> h3,
      "span" -> msg"pspDashboardAftReturnsCard.span.single".withArgs(startDateStr, endDate)
    )
  }



  /* Returns a seq of the aftReturns in their first compile have been zeroed out due to deletion of all charges
  */
  private def retrieveZeroedOutReturns(overview: Seq[AFTOverview], pstr: String)
                                      (implicit hc: HeaderCarrier): Future[Seq[AFTOverview]] = {
    val firstCompileReturns = overview.filter(_.compiledVersionAvailable).filter(_.numberOfVersions == 1)

    Future.sequence(firstCompileReturns.map(aftReturn =>
      aftConnector.getIsAftNonZero(pstr, aftReturn.periodStartDate.toString, "1"))).map {
      isNonZero => (firstCompileReturns zip isNonZero).filter(!_._2).map(_._1)
    }
  }

  private def getPastReturnsModelOpt(overview: Seq[AFTOverview], srn: String): Option[AFTViewModel] = {
    val pastReturns = overview.filter(!_.compiledVersionAvailable)

    if (pastReturns.nonEmpty) {
      Some(AFTViewModel(None, None, Link("aftAmendLink", appConfig.aftAmendUrl.format(srn), msg"aftPartial.view.change.past")))
    } else {
      None
    }
  }

  private def pspAftDashboardGetInProgressReturnsModel(
                                                        overview: Seq[AFTOverview],
                                                        srn: String,
                                                        pstr: String
                                                      )(
                                                        implicit
                                                        hc: HeaderCarrier
                                                      ): Future[Option[Link]] = {
    val inProgressReturns = overview.filter(_.compiledVersionAvailable)

    if (inProgressReturns.size == 1) {
      val startDate: LocalDate = inProgressReturns.head.periodStartDate
      val endDate: LocalDate = Quarters.getQuarter(startDate).endDate

      if (inProgressReturns.head.numberOfVersions == 1) {
        aftConnector.getIsAftNonZero(pstr, startDate.toString, "1").flatMap {
          case true => pspAftDashboardSingleInProgressReturnLink(srn, startDate, endDate, inProgressReturns.head)
          case _ => Future.successful(None)
        }
      } else {
        pspAftDashboardSingleInProgressReturnLink(srn, startDate, endDate, inProgressReturns.head)
      }

    } else if (inProgressReturns.nonEmpty) {
      pspAftDashboardMultipleInProgressReturnLink(srn, pstr, inProgressReturns)
    } else {
      Future.successful(None)
    }
  }

  private def pspAftDashboardSingleInProgressReturnLink(
                                                         srn: String,
                                                         startDate: LocalDate,
                                                         endDate: LocalDate,
                                                         overview: AFTOverview
                                                       )(
                                                         implicit hc: HeaderCarrier
                                                       ): Future[Option[Link]] = {
    aftCacheConnector.lockDetail(srn, startDate.toString).map {
      case Some(_) =>
        Some(Link(
          id = "aftSummaryLink",
          url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, overview.numberOfVersions),
          linkText = msg"pspDashboardAftReturnsCard.inProgressReturns.link.single.locked",
          hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(
            startDate.format(dateFormatterStartDate),
            endDate.format(dateFormatterDMY)
          ))
        ))
      case _ =>
        Some(Link(
          id = "aftSummaryLink",
          url = appConfig.aftSummaryPageUrl.format(srn, startDate, Draft, overview.numberOfVersions),
          linkText = msg"pspDashboardAftReturnsCard.inProgressReturns.link.single",
          hiddenText = Some(msg"aftPartial.view.hidden.forPeriod".withArgs(
            startDate.format(dateFormatterStartDate),
            endDate.format(dateFormatterDMY)
          ))
        ))
    }
  }

  private def pspAftDashboardMultipleInProgressReturnLink(
                                                           srn: String,
                                                           pstr: String,
                                                           inProgressReturns: Seq[AFTOverview]
                                                         )(
                                                           implicit hc: HeaderCarrier
                                                         ): Future[Option[Link]] = {
    retrieveZeroedOutReturns(inProgressReturns, pstr).map { zeroedReturns =>

      val countInProgress: Int = inProgressReturns.size - zeroedReturns.size

      if (countInProgress > 0) {

        Some(Link(
          id = "aftContinueInProgressLink",
          url = appConfig.aftContinueReturnUrl.format(srn),
          linkText = msg"pspDashboardAftReturnsCard.inProgressReturns.link",
          hiddenText = Some(msg"aftPartial.view.hidden")
        ))
      } else {
        None
      }
    }
  }

  def retrievePsaPenaltiesCardModel(psaFs: Seq[PsaFS], psaId: String)
    (implicit messages: Messages): DashboardAftViewModel = {

    val upcomingCharges: Seq[PsaFS] =
      psaFs.filter(_.dueDate.exists(_.isBefore(DateHelper.today)))

    val subHeadingPaymentDue = {

      val totalUpcoming = upcomingCharges.map(_.amountDue).sum

      val span = if (upcomingCharges.map(_.dueDate).distinct.size == 1) {
          msg"pspDashboardUpcomingAftChargesCard.span.singleDueDate".withArgs(
            upcomingCharges.map(_.dueDate).distinct.flatten.head.format(DateTimeFormatter.ofPattern("d MMMM yyyy")))
        } else {
          msg"pspDashboardUpcomingAftChargesCard.span.multipleDueDate"
        }

      Json.obj(
        "total" -> s"${FormatHelper.formatCurrencyAmountAsString(totalUpcoming)}",
        "span" -> span
      )
    }

    val subHeadingTotalOverduePayments = {
      val pastDueDateCharges: Seq[PsaFS] =
        psaFs.filter(charge =>  charge.dueDate.exists(_.isBefore(DateHelper.today)))
      val totalOverdue: BigDecimal = pastDueDateCharges.map(_.amountDue).sum
      Json.obj(
        "total" -> s"${FormatHelper.formatCurrencyAmountAsString(totalOverdue)}",
        "span" -> msg"pspDashboardOverdueAftChargesCard.total.span"
      )
    }

    val upcomingLink: Seq[Link] = if(upcomingCharges.nonEmpty) {
        Seq(Link("upcoming-penalties-id", appConfig.viewUpcomingPenaltiesUrl, msg"psaPenaltiesCard.paymentsDue.linkText", None))
    } else {
      Nil
    }

    DashboardAftViewModel(
      subHeadings = Seq(subHeadingPaymentDue, subHeadingTotalOverduePayments),
      links = upcomingLink :+ Link("past-penalties-id", appConfig.viewPenaltiesUrl, msg"psaPenaltiesCard.viewPastPenalties", None))
  }
}
