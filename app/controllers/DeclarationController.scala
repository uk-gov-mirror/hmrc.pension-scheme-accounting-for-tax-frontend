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

package controllers

import java.time.{LocalDate, LocalDateTime}

import config.FrontendAppConfig
import connectors.EmailConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import javax.inject.Inject
import models.LocalDateBinder._
import models.{Declaration, GenericViewModel, NormalMode, Quarter}
import navigators.CompoundNavigator
import pages.{AFTStatusQuery, DeclarationPage}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.AFTService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate, dateFormatterSubmittedDate}

import scala.concurrent.{ExecutionContext, Future}

class DeclarationController @Inject()(
    override val messagesApi: MessagesApi,
    identify: IdentifierAction,
    getData: DataRetrievalAction,
    requireData: DataRequiredAction,
    allowAccess: AllowAccessActionProvider,
    allowSubmission: AllowSubmissionAction,
    aftService: AFTService,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    navigator: CompoundNavigator,
    val controllerComponents: MessagesControllerComponents,
    config: FrontendAppConfig,
    renderer: Renderer,
    emailConnector: EmailConnector
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen allowAccess(srn, startDate)
      andThen allowSubmission andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val viewModel = GenericViewModel(
          submitUrl = routes.DeclarationController.onSubmit(srn, startDate).url,
          returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
          schemeName = schemeName
        )
        renderer.render(template = "declaration.njk", Json.obj(fields = "viewModel" -> viewModel)).map(Ok(_))
      }
    }

  def onSubmit(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen allowAccess(srn, startDate)
      andThen allowSubmission andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeNameWithPSTREmailAndQuarter { (schemeName, pstr, email, quarter) =>
        for {
          answersWithDeclaration <- Future.fromTry(request.userAnswers.set(DeclarationPage, Declaration("PSA", request.psaId.id, hasAgreed = true)))
          updatedStatus <- Future.fromTry(answersWithDeclaration.set(AFTStatusQuery, value = "Submitted"))
          _ <- userAnswersCacheConnector.save(request.internalId, updatedStatus.data)
          _ <- aftService.fileAFTReturn(pstr, updatedStatus)
          _ <- emailConnector.sendEmail(email, config.fileAFTReturnTemplateId, emailParams(schemeName, quarter))
        } yield {
          Redirect(navigator.nextPage(DeclarationPage, NormalMode, request.userAnswers, srn, startDate))
        }
      }
    }

  private def emailParams(schemeName: String, quarter: Quarter)(implicit messages: Messages): Map[String, String] = {
    val quarterStartDate = quarter.startDate.format(dateFormatterStartDate)
    val quarterEndDate = quarter.endDate.format(dateFormatterDMY)
    val submittedDate = dateFormatterSubmittedDate.format(LocalDateTime.now())
    val hmrcEmail = messages("confirmation.whatNext.send.to.email.id")
    val accountingPeriod = messages("confirmation.table.accounting.period.value", quarterStartDate, quarterEndDate)

    Map(
      "schemeName" -> schemeName,
      "accountingPeriod" -> accountingPeriod,
      "dateSubmitted" -> submittedDate,
      "hmrcEmail" -> hmrcEmail
    )
  }
}
