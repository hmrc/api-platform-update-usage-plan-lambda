package uk.gov.hmrc.apiplatform.updateusageplan

import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model.Op.ADD
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.api_platform_manage_api.utils.JsonMapper

import scala.jdk.CollectionConverters._

class UpdateUsagePlanHandlerSpec extends AnyWordSpec with Matchers with MockitoSugar with JsonMapper {

  trait Setup {
    val apiId: String = UUID.randomUUID().toString
    val usagePlanId: String = UUID.randomUUID().toString
    val patchOperation = PatchOp(ADD.toString, "/apiStages", s"$apiId:current")
    val usagePlanUpdateMsg: UsagePlanUpdateMsg = UsagePlanUpdateMsg(usagePlanId, Seq(patchOperation))
    val requestBody: String = toJson(usagePlanUpdateMsg)
    val message = new SQSMessage()
    message.setBody(requestBody)
    val sqsEvent = new SQSEvent()
    sqsEvent.setRecords(List(message).asJava)

    val mockAPIGatewayClient: ApiGatewayClient = mock[ApiGatewayClient]
    val mockContext: Context = mock[Context]
    when(mockContext.getLogger).thenReturn(mock[LambdaLogger])

    val updateUsagePlanHandler = new UpdateUsagePlanHandler(mockAPIGatewayClient, 1)
  }

  "Update usage plan handler" should {
    "update the usage plan" in new Setup {
      when(mockAPIGatewayClient.updateUsagePlan(any[UpdateUsagePlanRequest])).thenReturn(UpdateUsagePlanResponse.builder().build())

      updateUsagePlanHandler.handleInput(sqsEvent, mockContext)

      val updateUsagePlanRequestCaptor = ArgumentCaptor.forClass(classOf[UpdateUsagePlanRequest])
      verify(mockAPIGatewayClient).updateUsagePlan(updateUsagePlanRequestCaptor.capture)
      val capturedRequest: UpdateUsagePlanRequest = updateUsagePlanRequestCaptor.getValue
      capturedRequest.usagePlanId shouldBe usagePlanId
      exactly(1, capturedRequest.patchOperations) should have (Symbol("op") (Op.fromValue(patchOperation.op)), Symbol("path") (patchOperation.path), Symbol("value") (patchOperation.value))
    }

    "retry TooManyRequestsException" in new Setup {
      when(mockAPIGatewayClient.updateUsagePlan(any[UpdateUsagePlanRequest]))
        .thenThrow(TooManyRequestsException.builder().retryAfterSeconds("1").message("something went wrong").build())
        .thenReturn(UpdateUsagePlanResponse.builder().build())

      updateUsagePlanHandler.handleInput(sqsEvent, mockContext)

      verify(mockAPIGatewayClient, times(2)).updateUsagePlan(any[UpdateUsagePlanRequest])
    }

    "not fail when ConflictException is thrown by AWS SDK when updating the usage plan" in new Setup {
      when(mockAPIGatewayClient.updateUsagePlan(any[UpdateUsagePlanRequest])).thenThrow(ConflictException.builder().message("something went wrong").build())

      updateUsagePlanHandler.handleInput(sqsEvent, mockContext)
    }

    "propagate UnauthorizedException thrown by AWS SDK when updating the usage plan" in new Setup {
      val errorMessage = "something went wrong"
      when(mockAPIGatewayClient.updateUsagePlan(any[UpdateUsagePlanRequest])).thenThrow(UnauthorizedException.builder().message(errorMessage).build())

      val exception: UnauthorizedException = intercept[UnauthorizedException](updateUsagePlanHandler.handleInput(sqsEvent, mockContext))
      exception.getMessage shouldEqual errorMessage
      }

    "throw exception if the event has no messages" in new Setup {
      sqsEvent.setRecords(List().asJava)

      val exception: IllegalArgumentException = intercept[IllegalArgumentException](updateUsagePlanHandler.handleInput(sqsEvent, mockContext))
      exception.getMessage shouldEqual "Invalid number of records: 0"
    }

    "throw exception if the event has multiple messages" in new Setup {
      sqsEvent.setRecords(List(message, message).asJava)

      val exception: IllegalArgumentException = intercept[IllegalArgumentException](updateUsagePlanHandler.handleInput(sqsEvent, mockContext))
      exception.getMessage shouldEqual "Invalid number of records: 2"
    }
  }
}
