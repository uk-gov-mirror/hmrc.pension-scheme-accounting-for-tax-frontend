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

import behaviours.ControllerBehaviours
import controllers.base.ControllerSpecBase
import data.SampleData
import forms.chargeD.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.chargeD.ChargeDDetails
import models.{GenericViewModel, NormalMode, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import pages.chargeD.{ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{redirectLocation, route, status, _}
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport, Radios}

class ChargeDetailsControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with ControllerBehaviours {
  private val templateToBeRendered = "chargeD/chargeDetails.njk"
  private val form = new ChargeDetailsFormProvider()()
  private def chargeDetailsGetRoute: String = controllers.chargeD.routes.ChargeDetailsController.onPageLoad(NormalMode, SampleData.srn, 0).url
  private def chargeDetailsPostRoute: String = controllers.chargeD.routes.ChargeDetailsController.onSubmit(NormalMode, SampleData.srn, 0).url

  private val valuesValid: Map[String, Seq[String]] = Map(

  "dateOfEvent.day" -> Seq("3"),
  "dateOfEvent.month" -> Seq("4"),
  "dateOfEvent.year" -> Seq("2019"),
    "taxAt25Percent" -> Seq("33.44"),
    "taxAt55Percent" -> Seq("50.00")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
  "dateOfEvent.day" -> Seq("32"),
  "dateOfEvent.month" -> Seq("13"),
  "dateOfEvent.year" -> Seq("2003"),
    "taxAt25Percent" -> Seq("33.44"),
    "taxAt55Percent" -> Seq("33.44")
  )

  private val jsonToPassToTemplate:Form[ChargeDDetails]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeD.routes.ChargeDetailsController.onSubmit(NormalMode, SampleData.srn, 0).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName),
    "date" -> DateInput.localDate(form("dateOfEvent")),
    "memberName" -> "first last"
  )

  val validData: UserAnswers = SampleData.userAnswersWithSchemeName.set(MemberDetailsPage(0), SampleData.memberDetails).get
  val expectedJson: JsObject = validData.set(ChargeDetailsPage(0), SampleData.chargeDDetails).get.data

  "ChargeDetails Controller" must {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(validData)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(chargeDetailsGetRoute)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate.apply(form))

      application.stop()
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = validData.set(ChargeDetailsPage(0), SampleData.chargeDDetails).get

      val application = applicationBuilder(userAnswers = Some(ua)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(chargeDetailsGetRoute)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(form.fill(SampleData.chargeDDetails)))

      application.stop()
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, httpGETRequest(chargeDetailsGetRoute)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      when(mockCompoundNavigator.nextPage(Matchers.eq(ChargeDetailsPage(0)), any(), any(), any())).thenReturn(SampleData.dummyCall)

      val application = applicationBuilder(userAnswers = Some(validData)).build()

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(chargeDetailsPostRoute, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())
      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(SampleData.dummyCall.url)

      application.stop()
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      val application = applicationBuilder(userAnswers = Some(validData)).build()

      val result = route(application, httpPOSTRequest(chargeDetailsPostRoute, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())

      application.stop()
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, httpPOSTRequest(chargeDetailsPostRoute, valuesValid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
      application.stop()
    }
  }
}
