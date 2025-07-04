package uk.gov.hmrc.apiplatform.updateusageplan

import java.lang.Thread.sleep

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.api_platform_manage_api.AwsApiGatewayClient.awsApiGatewayClient
import uk.gov.hmrc.api_platform_manage_api.utils.SqsHandler

import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.Try

class UpdateUsagePlanHandler(apiGatewayClient: ApiGatewayClient, retryIntervalInSeconds: Int) extends SqsHandler {

  def this() = {
    this(awsApiGatewayClient, 20)
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
      case _: TooManyRequestsException =>
        logger.log(s"Too many requests. Retrying in $retryIntervalInSeconds seconds")
        sleep(retryIntervalInSeconds * 1000)
        updateUsagePlan(usagePlanUpdateMsg, patchOperations)
    } get
  }

  def updateUsagePlan(usagePlanUpdateMsg: UsagePlanUpdateMsg, patchOperations: Seq[PatchOperation])(implicit logger: LambdaLogger): Unit = {
    apiGatewayClient.updateUsagePlan(
      UpdateUsagePlanRequest.builder()
        .usagePlanId(usagePlanUpdateMsg.usagePlanId)
        .patchOperations(patchOperations.asJava)
        .build())
    logger.log(s"Updated usage plan ${usagePlanUpdateMsg.usagePlanId}")
  }
}

case class UsagePlanUpdateMsg(usagePlanId: String, patchOperations: Seq[PatchOp])
case class PatchOp(op: String, path: String, value: String)
