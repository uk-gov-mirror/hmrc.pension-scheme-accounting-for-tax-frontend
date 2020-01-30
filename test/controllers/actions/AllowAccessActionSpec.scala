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

package controllers.actions

import connectors.SchemeDetailsConnector
import controllers.base.ControllerSpecBase
import data.SampleData
import handlers.ErrorHandler
import models.requests.OptionalDataRequest
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import play.api.mvc.Result
import play.api.mvc.Results._
import play.api.test.Helpers._
import uk.gov.hmrc.domain.PsaId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AllowAccessActionSpec extends ControllerSpecBase with ScalaFutures {

  class TestHarness(
                     srn:String,
                     pensionsSchemeConnector: SchemeDetailsConnector,
                     errorHandler: ErrorHandler
                   )(implicit ec: ExecutionContext) extends AllowAccessAction(srn, pensionsSchemeConnector, errorHandler) {
    def test(optionalDataRequest:OptionalDataRequest[_]):Future[Option[Result]] = this.filter(optionalDataRequest)
  }

  "Allow Access Action" must {
    "respond with None (i.e. allow access) when there is an association" in {
      val pensionsSchemeConnector: SchemeDetailsConnector = mock[SchemeDetailsConnector]
      val errorHandler: ErrorHandler = mock[ErrorHandler]

      reset(pensionsSchemeConnector, errorHandler)
      when(pensionsSchemeConnector.checkForAssociation(any(), any())(any(),any(), any()))
        .thenReturn(Future.successful(true))

      val optionalDataRequest = OptionalDataRequest(fakeRequest, "", PsaId(SampleData.psaId), Option(SampleData.userAnswersWithSchemeName))

      val testHarness = new TestHarness("", pensionsSchemeConnector, errorHandler)
      whenReady( testHarness.test(optionalDataRequest)) { result =>
        result mustBe None
      }
    }

    "respond with a call to the error handler for 404 (i.e. don't allow access) when there is no association" in {
      val pensionsSchemeConnector: SchemeDetailsConnector = mock[SchemeDetailsConnector]
      val errorHandler: ErrorHandler = mock[ErrorHandler]
      val errorResult = Ok("error")
      reset(pensionsSchemeConnector, errorHandler)
      when(pensionsSchemeConnector.checkForAssociation(any(), any())(any(),any(), any()))
        .thenReturn(Future.successful(false))
      when(errorHandler.onClientError(any(), Matchers.eq(NOT_FOUND),any())).thenReturn(Future.successful(errorResult))

      val optionalDataRequest = OptionalDataRequest(fakeRequest, "", PsaId(SampleData.psaId), Option(SampleData.userAnswersWithSchemeName))

      val testHarness = new TestHarness("", pensionsSchemeConnector, errorHandler)
      whenReady( testHarness.test(optionalDataRequest)) { result =>
        result mustBe Some(errorResult)
      }
    }
  }
}
