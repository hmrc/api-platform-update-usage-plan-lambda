
# api-platform-update-usage-plan-lambda

Lambda function to update a usage plan in AWS API Gateway.

The `event` for the Lambda function is an SQS message. The body of the SQS message is JSON. For example:
```
{
    "usagePlanId": "A_USAGE_PLAN_ID",
    "patchOperations": [
        {
            "op": "add",
            "path": "/apiStages",
            "value": "API_STAGE"
        }
    ]
}
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
