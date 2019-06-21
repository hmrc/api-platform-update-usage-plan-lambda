package uk.gov.hmrc.apiplatform.updateusageplan

import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest._
import org.scalatest.mockito.MockitoSugar
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model.Op.ADD
import software.amazon.awssdk.services.apigateway.model.{Op, UnauthorizedException, UpdateUsagePlanRequest, UpdateUsagePlanResponse}
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.JsonMapper

import scala.collection.JavaConversions._

class UpdateUsagePlanHandlerSpec extends WordSpecLike with Matchers with MockitoSugar with JsonMapper {

  trait Setup {
    val apiId: String = UUID.randomUUID().toString
    val usagePlanId: String = UUID.randomUUID().toString
    val patchOperation = PatchOp(ADD.toString, "/apiStages", s"$apiId:current")
    val usagePlanUpdateMsg: UsagePlanUpdateMsg = UsagePlanUpdateMsg(usagePlanId, Seq(patchOperation))
    val requestBody: String = toJson(usagePlanUpdateMsg)
    val message = new SQSMessage()
    message.setBody(requestBody)
    val sqsEvent = new SQSEvent()
    sqsEvent.setRecords(List(message))

    val mockAPIGatewayClient: ApiGatewayClient = mock[ApiGatewayClient]
    val mockContext: Context = mock[Context]
    when(mockContext.getLogger).thenReturn(mock[LambdaLogger])

    val updateUsagePlanHandler = new UpdateUsagePlanHandler(mockAPIGatewayClient)
  }

  "Update usage plan handler" should {
    "update the usage plan" in new Setup {
      val updateUsagePlanRequestCaptor: ArgumentCaptor[UpdateUsagePlanRequest] = ArgumentCaptor.forClass(classOf[UpdateUsagePlanRequest])
      when(mockAPIGatewayClient.updateUsagePlan(updateUsagePlanRequestCaptor.capture())).thenReturn(UpdateUsagePlanResponse.builder().build())

      updateUsagePlanHandler.handleInput(sqsEvent, mockContext)

      val capturedRequest: UpdateUsagePlanRequest = updateUsagePlanRequestCaptor.getValue
      capturedRequest.usagePlanId shouldBe usagePlanId
      exactly(1, capturedRequest.patchOperations) should have ('op (Op.fromValue(patchOperation.op)), 'path (patchOperation.path), 'value (patchOperation.value))
    }

    "propagate UnauthorizedException thrown by AWS SDK when updating the usage plan" in new Setup {
      val errorMessage = "something went wrong"
      when(mockAPIGatewayClient.updateUsagePlan(any[UpdateUsagePlanRequest])).thenThrow(UnauthorizedException.builder().message(errorMessage).build())

      val exception: UnauthorizedException = intercept[UnauthorizedException](updateUsagePlanHandler.handleInput(sqsEvent, mockContext))
      exception.getMessage shouldEqual errorMessage
      }

    "throw exception if the event has no messages" in new Setup {
      sqsEvent.setRecords(List())

      val exception: IllegalArgumentException = intercept[IllegalArgumentException](updateUsagePlanHandler.handleInput(sqsEvent, mockContext))
      exception.getMessage shouldEqual "Invalid number of records: 0"
    }

    "throw exception if the event has multiple messages" in new Setup {
      sqsEvent.setRecords(List(message, message))

      val exception: IllegalArgumentException = intercept[IllegalArgumentException](updateUsagePlanHandler.handleInput(sqsEvent, mockContext))
      exception.getMessage shouldEqual "Invalid number of records: 2"
    }
  }
}
