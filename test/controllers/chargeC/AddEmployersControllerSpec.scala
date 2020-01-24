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

import controllers.base.ControllerSpecBase
import data.SampleData
import forms.AddMembersFormProvider
import matchers.JsonMatchers
import models.GenericViewModel
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import pages.chargeC._
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{redirectLocation, route, status, _}
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import scala.concurrent.Future

class AddEmployersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val templateToBeRendered = "chargeC/addEmployers.njk"
  private val form = new AddMembersFormProvider()("chargeC.addEmployers.error")
  private def httpPathGET: String = controllers.chargeC.routes.AddEmployersController.onPageLoad(SampleData.srn).url
  private def httpPathPOST: String = controllers.chargeC.routes.AddEmployersController.onSubmit(SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq("true")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map.empty

  private val cssQuarterWidth = "govuk-!-width-one-quarter"
  private val cssHalfWidth = "govuk-!-width-one-half"

  private def table = Json.obj(
    "firstCellIsHeader" -> false,
    "head" -> Json.arr(
      Json.obj("text" -> "Sponsoring employer", "classes" -> cssHalfWidth),
      Json.obj("text" -> "Total", "classes" -> cssQuarterWidth),
      Json.obj("text" -> ""),
      Json.obj("text" -> "")
    ),
    "rows" -> Json.arr(
      Json.arr(
        Json.obj("text" -> "First Last","classes" -> cssHalfWidth),
        Json.obj("text" -> "£33.44","classes" -> cssQuarterWidth),
        Json.obj("html" -> "<a id=employer-0-view href=/manage-pension-scheme-accounting-for-tax/aa/new-return/authorised-surplus-payments-charge/1/check-your-answers> View<span class= govuk-visually-hidden>First Last’s authorised surplus payments charge</span> </a>","classes" -> cssQuarterWidth),
        Json.obj("html" -> "<a id=employer-0-remove href=/manage-pension-scheme-accounting-for-tax/aa/new-return/authorised-surplus-payments-charge/1/remove-charge> Remove<span class= govuk-visually-hidden>First Last’s authorised surplus payments charge</span> </a>","classes" -> cssQuarterWidth)
      ),
      Json.arr(
        Json.obj("text" -> "Big Company","classes" -> cssHalfWidth),
        Json.obj("text" -> "£33.44","classes" -> cssQuarterWidth),
        Json.obj("html" -> "<a id=employer-1-view href=/manage-pension-scheme-accounting-for-tax/aa/new-return/authorised-surplus-payments-charge/2/check-your-answers> View<span class= govuk-visually-hidden>Big Company’s authorised surplus payments charge</span> </a>","classes" -> cssQuarterWidth),
        Json.obj("html" -> "<a id=employer-1-remove href=/manage-pension-scheme-accounting-for-tax/aa/new-return/authorised-surplus-payments-charge/2/remove-charge> Remove<span class= govuk-visually-hidden>Big Company’s authorised surplus payments charge</span> </a>","classes" -> cssQuarterWidth)
      ),
      Json.arr(
        Json.obj("text" -> "Total", "classes" -> "govuk-table__header--numeric"),
        Json.obj("text" -> "£66.88","classes" -> cssQuarterWidth),
        Json.obj("text" -> ""),
        Json.obj("text" -> "")
      )
    )
  )

  private val jsonToPassToTemplate:Form[Boolean]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeC.routes.AddEmployersController.onSubmit(SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName),
    "radios" -> Radios.yesNo(form("value")),
    "quarterStart" -> "1 April 2020",
    "quarterEnd" -> "30 June 2020",
    "table" -> table
  )

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }

  private def ua = SampleData.userAnswersWithSchemeName
    .set(IsSponsoringEmployerIndividualPage(0), true).toOption.get
    .set(IsSponsoringEmployerIndividualPage(1), false).toOption.get
    .set(SponsoringIndividualDetailsPage(0), SampleData.sponsoringIndividualDetails).toOption.get
    .set(SponsoringOrganisationDetailsPage(1), SampleData.sponsoringOrganisationDetails).toOption.get
    .set(ChargeCDetailsPage(0), SampleData.chargeCDetails).toOption.get
    .set(ChargeCDetailsPage(1), SampleData.chargeCDetails).toOption.get
    .set(TotalChargeAmountPage, BigDecimal(66.88)).toOption.get
  val expectedJson: JsObject = ua.set(AddEmployersPage, true).get.data

  "AddEmployers Controller" must {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(ua)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate.apply(form))

      application.stop()
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      when(mockCompoundNavigator.nextPage(Matchers.eq(AddEmployersPage), any(), any(), any())).thenReturn(SampleData.dummyCall)

      val application = applicationBuilder(userAnswers = Some(ua)).build()

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())
      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(SampleData.dummyCall.url)

      application.stop()
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      val application = applicationBuilder(userAnswers = Some(ua)).build()

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())

      application.stop()
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
      application.stop()
    }
  }
}
