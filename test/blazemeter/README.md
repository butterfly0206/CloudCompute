# CloudOS-compute
Platform for batch style computational services

## Blazemeter testing

Do a vault login, export sandbox-ready credentials into your environment 
```
export AWS_REGION=us-west-2
export AWS_DEFAULT_REGION=us-west-2
export AWS_PROFILE=cosv2-c-uw2-sb
export VAULT_ADDR=https://civ1.dv.adskengineer.net:8200
export AWS_ACCESS_KEY_ID=<you got this from the vault login, right?>
export AWS_SESSION_TOKEN==<you got this from the vault login, right?>
```

And let's assume you have the AWS credentials stored
```
echo "[cosv2-c-uw2-sb]" >> ~/.aws/credentials
echo "aws_access_key_id=$AWS_ACCESS_KEY_ID" >> ~/.aws/credentials
echo "aws_secret_access_key=$AWS_SECRET_ACCESS_KEY" >> ~/.aws/credentials
echo "aws_session_token=$AWS_SESSION_TOKEN" >> ~/.aws/credentials
```

Given all that, run ./cleanup-sb.sh from this folder

Then, refresh your credentials, and run ./blazemeter-sb.sh from this folder