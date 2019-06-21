package uk.gov.hmrc.apiplatform.updateusageplan

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import software.amazon.awssdk.services.apigateway.ApiGatewayClient
import software.amazon.awssdk.services.apigateway.model._
import uk.gov.hmrc.api_platform_manage_api.AwsApiGatewayClient.awsApiGatewayClient
import uk.gov.hmrc.aws_gateway_proxied_request_lambda.SqsHandler

import scala.collection.JavaConverters._
import scala.language.postfixOps

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

    val patchOperations = usagePlanUpdateMsg.patchOperations.map(po => PatchOperation.builder().op(po.op).path(po.path).value(po.value).build())
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
