> [!WARNING]
> **This repository has been archived**
> 
> IBM Legacy Public Repository Disclosure: All content in this repository including code has been provided by IBM under the associated open source software license and IBM is under no obligation to provide enhancements, updates, or support.
> IBM developers produced this code as an open source project (not as an IBM product), and IBM makes no assertions as to the level of quality nor security, and will not be maintaining this code going forward.

# cics-java-liberty-tai-jwt

Sample WebSphere Liberty Trust Association Interceptor (TAI) for use with CICS Liberty to validate [JSON web tokens (JWTs)](https://tools.ietf.org/html/rfc7519)
and set the authenticated user ID based on the JWT subject claim. This TAI sample can also be used with z/OS Liberty.

For detailed configuration instructions see [Configuring TAI in Liberty](https://www.ibm.com/support/knowledgecenter/en/SSEQTP_liberty/com.ibm.websphere.wlp.nd.multiplatform.doc/ae/twlp_sec_tai.html) and [Developing a custom TAI for Liberty](https://www.ibm.com/support/knowledgecenter/en/SSEQTP_liberty/com.ibm.websphere.wlp.nd.multiplatform.doc/ae/twlp_dev_custom_tai.html).
A TAI can be deployed as a JAR file and added to the Liberty library (as shown in this sample) **__OR__** as a Liberty feature (more information in the [Knowledge Center](https://www.ibm.com/support/knowledgecenter/SSEQTP_liberty/com.ibm.websphere.wlp.nd.multiplatform.doc/ae/twlp_feat_tai.html)).

## Introduction

You can configure Liberty to integrate with third-party security services by using a TAI. The TAI can be called before or after single sign-on (SSO), for more configuration options see the [Knowledge Center](https://www.ibm.com/support/knowledgecenter/SSEQTP_liberty/com.ibm.websphere.liberty.autogen.base.doc/ae/rwlp_config_trustAssociation.html).
You can develop a custom TAI class by implementing the [com.ibm.wsspi.security.tai.TrustAssociationInterceptor](https://www.ibm.com/support/knowledgecenter/en/SSEQTP_liberty/com.ibm.websphere.javadoc.liberty.doc/com.ibm.websphere.appserver.api.security_1.2-javadoc/com/ibm/wsspi/security/tai/TrustAssociationInterceptor.html) interface provided in the Liberty server.

The trust association interface is a service provider API that enables the integration of third-party security services with a Liberty server. When processing the web request, the Liberty server calls out and passes the `HttpServletRequest` and `HttpServletResponse` to the trust association interceptors. The `HttpServletRequest` calls the interceptor `isTargetInterceptor` method to determine whether the interceptor should catch the request. After an appropriate trust association interceptor is selected, the `HttpServletRequest` is processed by the interceptor `negotiateValidateandEstablishTrust` method, and the result is returned in a `TAIResult` object. You can add your own logic code in the TAI methods.

> Note: The use of a TAI should be handled with care, where possible use a standard supported mechanism within Liberty to achieve security architecture and integration goals. For instance, the validation of a JWT can be achieved by using built-in process in Liberty stand-alone v16.0.0.3 and later versions.

This sample JWTTAI Java class provides the following functions:

* JWT validation based on the JwtConsumer API (Liberty JWT feature) - the JWT signature will be checked, and the claims (exp, iss, aud) will be validated against the expected values;

* Assign the ***Principal*** identity by retrieving the ***subject*** claim;

## How does it work?

When the Liberty server is launched, it will read the jwtConsumer configuration named `myJWTConsumer` and make it available. The JWTTAI TAI will also be initialized, but in this sample nothing is done during the TAI initialization process.

JWTTAI will only intercept ***HTTPS*** requests that contain a ***JWT in the `Authorization Bearer` header***.
It will create an instance of a *JwtConsumer* based on the `myJWTConsumer` configuration to validate and parse JWTs.

Once the JWTTAI is selected to handle the authentication, it will validate the JWT signature with the `trustedAlias` certificate taken from `myJWTConsumer` and will parse the JWT to retrieve the claims. 
The JwtConsumer only verifies the issuer, audience and expiry claims; to validate the other claims some lines of code need to be written in the TAI `negotiateValidateandEstablishTrust` method.
If the JWT passes all the checks, the subject claim will be defined as the Principal identity and the request will be processed.
Otherwise the request will be rejected.

Be careful, the subject claim needs to match an entry in the user registry.

This sample shows how a TAI can handle a simple JWT use case.

## Customization and compilation

To use this sample download the code or clone this repository and load the JWTTAI.java file into your favorite text editor or IDE.

Cutomize the JWTTAI.java class, if necessary. 

To compile the JWTTAI.java class and generate the JAR file, the only Java library required is **__WebSphere Liberty libraries__**, available with CICS Explorer SDK.

Finally, upload the generated JAR file (as binary) to the appropriate zFS. In the example configuration, we have uploaded the JAR file in the *jars* folder (created manually) located in the server configuration directory (same directory as server.xml).

## Liberty configuration

To configure the Liberty server, add the following elements to the Liberty server.xml configuration:

```xml
<featureManager>
    <feature>cicsts:security-1.0</feature>
    <feature>appSecurity-2.0</feature>
    <feature>jwt-1.0</feature>
</featureManager>

<library id="myTAIlib">
    <fileset dir="${server.config.dir}/jars" includes="jwttai.jar"/>
</library>

<trustAssociation id="myTrustAssociation" failOverToAppAuthType="false" invokeForUnprotectedURI="false">
    <interceptors id="JWTTAI" className="com.ibm.cicsdev.tai.JWTTAI" invokeAfterSSO="true" invokeBeforeSSO="false" libraryRef="myTAIlib"/>
</trustAssociation>

<jwtConsumer id="myJWTConsumer" audiences="catalogManager" issuer="idg" signatureAlgorithm="RS256" trustStoreRef="JWTTrustStore" trustedAlias="<certificate_label_or_alias>"/>
<keyStore id="JWTTrustStore" .../>

```

This modification adds the necessary JAR file to a library that can then be referenced by the `trustAssociation` element. The variable `server.config.dir` points to the folder that contains the server.xml configuration file.
You may need to change the className attribute to match the name of your TAI class.
This also sets the failOverToAppAuthType attribute to false, so the application security is disabled.

The `jwtConsumer` tag specifies the values that are expected for different claims; update the `audiences` and `issuer` values to match the JWT generator configuration. The tag also specifies which public certificate to use (`trustedAlias`) to validate the JWT signature.
> Note 1: only the public certificate is required, no need to have the private key in the keyStore.

> Note 2: if using a SAF keyring with only a public certificate, do connect the certificate with usage **CERTAUTH**. 

More information on the `trustAssocation` and `interceptors` elements can be found on the [IBM Knowledge Center](https://www.ibm.com/support/knowledgecenter/en/SSEQTP_liberty/com.ibm.websphere.liberty.autogen.base.doc/ae/rwlp_config_trustAssociation.html).<br/>
More information on the `jwtConsumer` element can be found on the [IBM Knowledge Center](https://www.ibm.com/support/knowledgecenter/en/SSEQTP_liberty/com.ibm.websphere.liberty.autogen.base.doc/ae/rwlp_config_jwtConsumer.html).<br/>
More information on the different supported keyStore types on the [IBM Knowledge Center](https://www.ibm.com/support/knowledgecenter/en/SS7K4U_liberty/com.ibm.websphere.wlp.zseries.doc/ae/rwlp_sec_keystores.html).<br/>

## Testing the TAI

To test the TAI, invoke an existing application hosted on your Liberty server that requires authentication. The invokation needs to be an HTTPS request containing a JWT in the Authorization header. The easiest way to build and send an HTTPS request is to use a REST client.<br/>
Make sure to use a JWT that contains the expected claims and to use the right set of public/private keys. If everything goes well you should see that the transaction is run with the user ID provided in the subject claim. Otherwise check the messages.log file.

### Generate a JWT with Liberty

If you don't have a JWT generator at hand, you can use the Liberty server to do it for you.<br/>
Simply add the following `jwtBuilder` tag in the server.xml configuration file:

```xml
<jwtBuilder id="myJWTBuilder" audiences="<audiences_list>" issuer="<issuer_value>" keyAlias="<certificate_label_or_alias>" keyStoreRef="<keyStoreID>"/>
<keyStore id="<keyStoreID>" .../>
```
Replace the placeholders with the correct values.
> Note: This time the keyStore needs to contain the private key in order to sign the JWTs. If the keyStore defined earlier does contain the private key, it can be reused here instead of redefining a new keyStore.

More information on the `jwtBuilder` element on the [IBM Knowledge Center](https://www.ibm.com/support/knowledgecenter/en/SS7K4U_liberty/com.ibm.websphere.liberty.autogen.zos.doc/ae/rwlp_config_jwtBuilder.html).

The JWT feature exposes JWT builders with a REST API. A token can be retrieved by sending the HTTPS request:<br/>
**GET https://&lt;hostname&gt;:&lt;httpsPort&gt;/jwt/ibm/api/myJWTBuilder/token**<br/>
where `myJWTBuilder` is the id used by the configuration.

If the request is sent with a web browser, the browser will prompt for credentials and if the authentication succeeds a JWT will be returned.<br/>
If the request is sent with a REST client, the request needs to contain a Basic Auth header with the credentials.

Once the JWT retrieved, it should be added to the request as an HTTP Authorization header, for instance "Authorization: Bearer &lt;JWT&gt;".

