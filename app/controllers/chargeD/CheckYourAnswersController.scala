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

package controllers.chargeD

import java.time.LocalDate

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions.{AllowAccessActionProvider, DataRequiredAction, DataRetrievalAction, IdentifierAction}
import helpers.CYAChargeDService
import models.LocalDateBinder._
import models.chargeD.ChargeDDetails
import models.{GenericViewModel, Index, NormalMode}
import navigators.CompoundNavigator
import pages.{PSTRQuery, ViewOnlyAccessiblePage}
import pages.chargeD.{ChargeDetailsPage, CheckYourAnswersPage, TotalChargeAmountPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{AFTService, ChargeDService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, SummaryList}

import scala.concurrent.{ExecutionContext, Future}

class CheckYourAnswersController @Inject()(config: FrontendAppConfig,
                                           override val messagesApi: MessagesApi,
                                           identify: IdentifierAction,
                                           getData: DataRetrievalAction,
                                           allowAccess: AllowAccessActionProvider,
                                           requireData: DataRequiredAction,
                                           aftService: AFTService,
                                           userAnswersCacheConnector: UserAnswersCacheConnector,
                                           navigator: CompoundNavigator,
                                           val controllerComponents: MessagesControllerComponents,
                                           chargeDHelper: ChargeDService,
                                           renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String, startDate: LocalDate, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, Some(ViewOnlyAccessiblePage))).async { implicit request =>
      DataRetrievals.cyaChargeD(index, srn, startDate) { (memberDetails, chargeDetails, schemeName) =>
        val helper = new CYAChargeDService(srn, startDate)

        val seqRows: Seq[SummaryList.Row] = Seq(
          helper.chargeDMemberDetails(index, memberDetails),
          helper.chargeDDetails(index, chargeDetails),
          Seq(helper.total(chargeDetails.total))
        ).flatten

        renderer
          .render(
            "check-your-answers.njk",
            Json.obj(
              "srn" -> srn,
              "startDate" -> Some(startDate),
              "list" -> helper.rows(request.sessionData.isViewOnly, seqRows),
              "viewModel" -> GenericViewModel(
                submitUrl = routes.CheckYourAnswersController.onClick(srn, startDate, index).url,
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
                schemeName = schemeName
              ),
              "chargeName" -> "chargeD",
              "canChange" -> !request.sessionData.isViewOnly
            )
          )
          .map(Ok(_))
      }
    }

  def onClick(srn: String, startDate: LocalDate, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      (request.userAnswers.get(PSTRQuery), request.userAnswers.get(ChargeDetailsPage(index))) match {
        case (Some(pstr), Some(chargeDetails)) =>
          val totalAmount: BigDecimal = chargeDHelper.getLifetimeAllowanceMembers(request.userAnswers, srn, startDate).map(_.amount).sum

          val updatedChargeDetails: ChargeDDetails = chargeDetails.copy(
            taxAt25Percent = Option(chargeDetails.taxAt25Percent.getOrElse(BigDecimal(0.00))),
            taxAt55Percent = Option(chargeDetails.taxAt55Percent.getOrElse(BigDecimal(0.00)))
          )

          for {
            ua1 <- Future.fromTry(request.userAnswers.set(TotalChargeAmountPage, totalAmount))
            ua2 <- Future.fromTry(ua1.set(ChargeDetailsPage(index), updatedChargeDetails))
            _ <- userAnswersCacheConnector.save(request.internalId, ua2.data)
            _ <- aftService.fileAFTReturn(pstr, ua2)
          } yield {
            Redirect(navigator.nextPage(CheckYourAnswersPage, NormalMode, request.userAnswers, srn, startDate))
          }
        case _ =>
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
      }
    }
}
