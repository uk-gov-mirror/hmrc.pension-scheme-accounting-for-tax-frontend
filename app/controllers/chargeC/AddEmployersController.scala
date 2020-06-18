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

package controllers.chargeC

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.AddMembersFormProvider
import javax.inject.Inject
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AccessType, GenericViewModel, NormalMode, Quarter}
import navigators.CompoundNavigator
import pages.chargeC.AddEmployersPage
import pages.{QuarterPage, SchemeNameQuery, ViewOnlyAccessiblePage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.ChargeCService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.DateHelper.dateFormatterDMY

import scala.concurrent.{ExecutionContext, Future}

class AddEmployersController @Inject()(override val messagesApi: MessagesApi,
                                       userAnswersCacheConnector: UserAnswersCacheConnector,
                                       navigator: CompoundNavigator,
                                       identify: IdentifierAction,
                                       getData: DataRetrievalAction,
                                       allowAccess: AllowAccessActionProvider,
                                       requireData: DataRequiredAction,
                                       formProvider: AddMembersFormProvider,
                                       val controllerComponents: MessagesControllerComponents,
                                       chargeCHelper: ChargeCService,
                                       config: FrontendAppConfig,
                                       renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def form: Form[Boolean] = formProvider("chargeC.addEmployers.error")

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, Some(ViewOnlyAccessiblePage), version, accessType)).async { implicit request =>
      (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(QuarterPage)) match {
        case (Some(schemeName), Some(quarter)) =>
          renderer.render(template = "chargeC/addEmployers.njk",
            getJson(srn, startDate, form, schemeName, quarter, accessType, version)).map(Ok(_))

        case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
      }
    }

  def onSubmit(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async {
    implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => {
            (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(QuarterPage)) match {
              case (Some(schemeName), Some(quarter)) =>
                renderer
                  .render(template = "chargeC/addEmployers.njk",
                    getJson(srn, startDate, formWithErrors, schemeName, quarter, accessType, version))
                  .map(BadRequest(_))

              case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
            }
          },
          value => {
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(AddEmployersPage, value))
              _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
            } yield Redirect(navigator.nextPage(AddEmployersPage, NormalMode, updatedAnswers, srn, startDate, accessType, version))
          }
        )
  }

  private def getJson(srn: String, startDate: LocalDate, form: Form[_], schemeName: String, quarter: Quarter, accessType: AccessType, version: Int)(
      implicit request: DataRequest[AnyContent]): JsObject = {

    val viewModel = GenericViewModel(submitUrl = routes.AddEmployersController.onSubmit(srn, startDate, accessType, version).url,
                                     returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                                     schemeName = schemeName)

    val members = chargeCHelper.getSponsoringEmployers(request.userAnswers, srn, startDate, accessType, version)

    Json.obj(
      "srn" -> srn,
      "startDate" -> Some(startDate),
      "form" -> form,
      "viewModel" -> viewModel,
      "radios" -> Radios.yesNo(form("value")),
      "quarterStart" -> quarter.startDate.format(dateFormatterDMY),
      "quarterEnd" -> quarter.endDate.format(dateFormatterDMY),
      "table" -> Json.toJson(chargeCHelper.mapToTable(members, !request.isViewOnly)),
      "canChange" -> !request.isViewOnly
    )

  }

}
