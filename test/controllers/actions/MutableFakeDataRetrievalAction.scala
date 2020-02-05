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

import models.UserAnswers
import models.requests.{IdentifierRequest, OptionalDataRequest}

import scala.concurrent.{ExecutionContext, Future}

class MutableFakeDataRetrievalAction(viewOnly: Boolean = false) extends DataRetrievalAction {
  private var dataToReturn: Option[UserAnswers] = None
  def setDataToReturn(userAnswers: Option[UserAnswers]): Unit = dataToReturn = userAnswers

  override def apply(srn: String): DataRetrieval = new MutableFakeDataRetrieval(viewOnly, dataToReturn)
}

class MutableFakeDataRetrieval(viewOnly: Boolean = false, dataToReturn: Option[UserAnswers]) extends DataRetrieval {

  override protected def transform[A](request: IdentifierRequest[A]): Future[OptionalDataRequest[A]] =
    Future(OptionalDataRequest(request.request, s"srn-startDt-id", request.psaId, dataToReturn, viewOnly))

  override protected implicit val executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global
}
