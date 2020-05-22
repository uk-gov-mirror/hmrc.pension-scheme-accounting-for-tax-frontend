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

import java.time.LocalDate

import com.google.inject.Inject
import AddMembersService.mapChargeXMembersToTable
import helpers.{DeleteChargeHelper, FormatHelper}
import models.AmendedChargeStatus.{Unknown, amendedChargeStatus}
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.requests.DataRequest
import models.viewModels.ViewAmendmentDetails
import models.{Member, MemberDetails, UserAnswers}
import pages.chargeE.{ChargeDetailsPage, MemberAFTVersionPage, MemberStatusPage}
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, Call}
import viewmodels.Table

class ChargeEService @Inject()(deleteChargeHelper: DeleteChargeHelper) {

  def getAnnualAllowanceMembersIncludingDeleted(ua: UserAnswers, srn: String, startDate: LocalDate)
                                               (implicit request: DataRequest[AnyContent]): Seq[Member] = {

    val members = for {
      (member, index) <- ua.getAllMembersInCharge[MemberDetails]("chargeEDetails").zipWithIndex
    } yield {
      ua.get(ChargeDetailsPage(index)).map { chargeDetails =>
        Member(
          index,
          member.fullName,
          member.nino,
          chargeDetails.chargeAmount,
          viewUrl(index, srn, startDate).url,
          removeUrl(index, srn, startDate, ua).url,
          member.isDeleted
        )
      }
    }

    members.flatten
  }

  def getAllAnnualAllowanceAmendments(ua: UserAnswers)(implicit request: DataRequest[AnyContent]): Seq[ViewAmendmentDetails] = {
    ua.getAllMembersInCharge[MemberDetails]("chargeEDetails")
      .zipWithIndex
      .flatMap { memberDetails =>
        val (member, index) = memberDetails
        ua.get(ChargeDetailsPage(index)).map { chargeDetails =>
          val currentVersion = request.aftVersion
          val memberVersion = ua.get(MemberAFTVersionPage(index)).getOrElse(0)

          if (memberVersion == currentVersion) {
            Some(
              ViewAmendmentDetails(
                member.fullName,
                ChargeTypeAnnualAllowance.toString,
                FormatHelper.formatCurrencyAmountAsString(chargeDetails.chargeAmount),
                ua.get(MemberStatusPage(index)).map(amendedChargeStatus).getOrElse(Unknown)
              )
            )
          } else {
            None
          }
        }
      }
      .flatten
  }

  def getAnnualAllowanceMembers(ua: UserAnswers, srn: String, startDate: LocalDate)
                               (implicit request: DataRequest[AnyContent]): Seq[Member] =
    getAnnualAllowanceMembersIncludingDeleted(ua, srn, startDate).filterNot(_.isDeleted)

  def viewUrl(index: Int, srn: String, startDate: LocalDate): Call =
    controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, startDate, index)

  private def removeUrl(index: Int, srn: String, startDate: LocalDate, ua: UserAnswers)(implicit request: DataRequest[AnyContent]): Call =
    if(request.isAmendment && deleteChargeHelper.isLastCharge(ua)) {
      controllers.chargeE.routes.RemoveLastChargeController.onPageLoad(srn, startDate, index)
    } else {
      controllers.chargeE.routes.DeleteMemberController.onPageLoad(srn, startDate, index)
    }

  def mapToTable(members: Seq[Member], canChange: Boolean)(implicit messages: Messages): Table =
    mapChargeXMembersToTable("chargeE", members, canChange)

}
