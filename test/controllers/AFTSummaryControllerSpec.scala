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

import connectors.AFTConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.AFTSummaryFormProvider
import matchers.JsonMatchers
import models.{Enumerable, GenericViewModel, Quarter, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers, Mockito}
import org.scalatest.BeforeAndAfterEach
import pages.{AFTSummaryPage, QuarterPage}
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.SchemeService
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.AFTSummaryHelper

import scala.concurrent.Future

class AFTSummaryControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with BeforeAndAfterEach with Enumerable.Implicits {
  private val mockAftConnector: AFTConnector = mock[AFTConnector]

  private val mockSchemeService = mock[SchemeService]

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[SchemeService].toInstance(mockSchemeService),
      bind[AFTConnector].toInstance(mockAftConnector)
    )

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()

  private val application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private val templateToBeRendered = "aftSummary.njk"

  private val form = new AFTSummaryFormProvider()()

  private def httpPathGETNoVersion: String = controllers.routes.AFTSummaryController.onPageLoad(srn, None).url

  private def httpPathGETVersion: String = controllers.routes.AFTSummaryController.onPageLoad(srn, Some(version)).url

  private def httpPathPOST: String = controllers.routes.AFTSummaryController.onSubmit(srn, None).url

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq("true"))

  private val valuesInvalid: Map[String, Seq[String]] = Map("value" -> Seq("xyz"))

  private val summaryHelper = new AFTSummaryHelper

  private val uaGetAFTDetails: UserAnswers = UserAnswers().set(QuarterPage, Quarter("2000-04-01", "2000-05-31")).toOption.get

  override def beforeEach: Unit = {
    super.beforeEach()
    Mockito.reset(mockSchemeService, mockAftConnector, mockUserAnswersCacheConnector, mockRenderer)
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(uaGetAFTDetails.data))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
    when(mockAftConnector.getAFTDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(uaGetAFTDetails.data))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
  }


  private def jsonToPassToTemplate(version: Option[String]): Form[Boolean] => JsObject = form => Json.obj(
    "form" -> form,
    "list" -> summaryHelper.summaryListData(UserAnswers(), srn),
    "viewModel" -> GenericViewModel(
      submitUrl = routes.AFTSummaryController.onSubmit(srn, version).url,
      returnUrl = dummyCall.url,
      schemeName = schemeName),
    "radios" -> Radios.yesNo(form("value"))
  )

  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeName)

  "AFTSummary Controller" must {
    "return OK and the correct view for a GET where no version is present in the request" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGETNoVersion)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(version = None).apply(form))
    }

    "return OK and the correct view for a GET where a version is present in the request" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))

      val pstrCaptor = ArgumentCaptor.forClass(classOf[String])

      val startDateCaptor = ArgumentCaptor.forClass(classOf[String])

      val versionCaptor = ArgumentCaptor.forClass(classOf[String])

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGETVersion)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(version = Some(version)).apply(form))

      verify(mockAftConnector, times(1)).getAFTDetails(pstrCaptor.capture(), startDateCaptor.capture, versionCaptor.capture)(any(), any())

      pstrCaptor.getValue mustEqual pstr

      startDateCaptor.getValue mustEqual "2020-04-01"

      versionCaptor.getValue mustEqual version
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {
      when(mockCompoundNavigator.nextPage(Matchers.eq(AFTSummaryPage), any(), any(), any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())

      jsonCaptor.getValue must containJson(Json.obj(AFTSummaryPage.toString -> Json.toJson(true)))

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}
