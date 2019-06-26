package uk.gov.hmrc.apiplatform.updateusageplan

import java.lang.Thread.sleep

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.api_platform_manage_api.AwsApiGatewayClient.awsApiGatewayClient
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.SqsHandler

import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.util.Try

class UpdateUsagePlanHandler(apiGatewayClient: ApiGatewayClient) extends SqsHandler {

  def this() {
    this(awsApiGatewayClient)
  }

  override def handleInput(input: SQSEvent, context: Context): Unit = {
    implicit val logger: LambdaLogger = context.getLogger
    if (input.getRecords.size != 1) {
      throw new IllegalArgumentException(s"Invalid number of records: ${input.getRecords.size}")
    }

    val usagePlanUpdateMsg: UsagePlanUpdateMsg = fromJson[UsagePlanUpdateMsg](input.getRecords.get(0).getBody)
    val patchOperations: Seq[PatchOperation] = usagePlanUpdateMsg.patchOperations.map(po => PatchOperation.builder().op(po.op).path(po.path).value(po.value).build())

    Try {
      updateUsagePlan(usagePlanUpdateMsg, patchOperations)
    } recover {
      case e: ConflictException =>
        logger.log(e.getMessage)
      case e: TooManyRequestsException =>
        logger.log(s"Too many requests. Retrying in ${e.retryAfterSeconds} seconds")
        sleep(e.retryAfterSeconds.toInt * 1000)
        updateUsagePlan(usagePlanUpdateMsg, patchOperations)
    } get

    logger.log(s"Updated usage plan ${usagePlanUpdateMsg.usagePlanId}")
  }

  def updateUsagePlan(usagePlanUpdateMsg: UsagePlanUpdateMsg, patchOperations: Seq[PatchOperation]): Unit = {
    apiGatewayClient.updateUsagePlan(
      UpdateUsagePlanRequest.builder()
        .usagePlanId(usagePlanUpdateMsg.usagePlanId)
        .patchOperations(patchOperations.asJava)
        .build())
  }
}

case class UsagePlanUpdateMsg(usagePlanId: String, patchOperations: Seq[PatchOp])
case class PatchOp(op: String, path: String, value: String)
