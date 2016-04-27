# lein-s3-wagon-vault

A Leiningen (middleware) plugin to inject a "releases" private s3 
repository using AWS credentials obtained from [Hashicorp's Vault](https://www.vaultproject.io/docs/secrets/aws/). 

## Usage

### SSL Cert

If your vault server has an SSL certificate from Letsencrypt, and you's using 
Oracle's Java or an older java, you might have add the letsencrypt CA cert 
to your JVM.

There are other ways to do this, but we used this script:
https://gist.github.com/Firefishy/109b0f1a90156f6c933a50fe40aa777e

### project.clj 

Add into the `:plugins` vector of your project.clj (use the latest version 
numbers below).


[![Clojars Project](https://img.shields.io/clojars/v/ai.wadeandwendy/lein-s3-wagon-vault.svg)](https://clojars.org/ai.wadeandwendy/lein-s3-wagon-vault)


```
  :plugins [[lein-s3-wagon-vault "INSERT_VERSION"]
            [s3-wagon-private "1.2.0"]]
```

And add a s3 private repo to the `:repositories` key as per the 
[s3-private-wagon](https://github.com/technomancy/s3-wagon-private) instructions. 
In the repo definition, for :username and :passphrase specify `:vault/aws/creds/YOUR_KEY`
where "aws/creds/YOUR_KEY" is the path in vault for your AWS credentials.  This 
path must start wth "aws/creds/".

```
  :repositories [["releases"
                  {:url           "s3p://mvn.YOUR.DOMAIN/releases/"
                   :username      :vault/aws/creds/YOUR_KEY
                   :passphrase    :vault/aws/creds/YOUR_KEY
                   :sign-releases false}]]
```


### Environment 

This plugin requires several ENVIRONMENT VARIABLES for Vault:

```
export VAULT_ADDR="https://vault.your.domain"
export VAULT_TOKEN=`cat $HOME/.vault-token`
```

### AWS policy

You'll also need to set up an AWS policy as per Vault instructions granting 
read/write access to the s3 bucket mentioned above, for example:

```
vault write aws/roles/maven-repo policy=@tmp/maven-repo-policy.json
```

You can set this as a Vault inline policy or as an AWS policy (see Vault AWS 
mount docs).  Sample policy:

```
{
   "Version": "2012-10-17",
   "Statement": [
       {
           "Effect": "Allow",
           "Action": [
               "s3:List*",
               "s3:GetBucket*"
           ],
           "Resource": [
               "arn:aws:s3:::mvn.wwai.cloud"
           ]
       },
       {
           "Effect": "Allow",
           "Action": [
               "s3:Get*",
               "s3:Put*"
           ],
           "Resource": [
               "arn:aws:s3:::mvn.wwai.cloud/*"
           ]
       }
   ]
}
```

### Test

After adding the plugin to your project.clj, test it out with the 
lein-pprint(https://github.com/technomancy/leiningen/tree/master/lein-pprint) 
plugin:

```
lein pprint :repositories
```

## License

This project was developed at [Wade & Wendy](http://wadeandwendy.ai)

Copyright © 2016 Wade & Wendy

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
