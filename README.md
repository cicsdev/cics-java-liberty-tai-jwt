# cics-java-liberty-tai-jwt
Sample WebSphere Liberty Trust Association Interceptor (TAI) for use with CICS Liberty to validate [JSON web tokens (JWTs)](https://tools.ietf.org/html/rfc7519)
and set the authorized user ID based on the JWT subject claim.

For detailed configuration instructions see [Configuring TAI in Liberty](https://www.ibm.com/support/knowledgecenter/en/SSEQTP_liberty/com.ibm.websphere.wlp.nd.multiplatform.doc/ae/twlp_sec_tai.html) and [Developing a custom TAI for Liberty](https://www.ibm.com/support/knowledgecenter/en/SSEQTP_liberty/com.ibm.websphere.wlp.nd.multiplatform.doc/ae/twlp_dev_custom_tai.html).
A TAI can be deployed as a JAR file and added to the Liberty library (as shown in this description) **__OR__** as a Liberty feature (more information in the [Knowledge Center](https://www.ibm.com/support/knowledgecenter/SSEQTP_liberty/com.ibm.websphere.wlp.nd.multiplatform.doc/ae/twlp_feat_tai.html)).

## Introduction

You can configure Liberty to integrate with third-party security services by using a TAI. The TAI can be called before or after single sign-on (SSO).
You can develop a custom TAI class by implementing the [com.ibm.wsspi.security.tai.TrustAssociationInterceptor](https://www.ibm.com/support/knowledgecenter/en/SSEQTP_liberty/com.ibm.websphere.javadoc.liberty.doc/com.ibm.websphere.appserver.api.security_1.2-javadoc/com/ibm/wsspi/security/tai/TrustAssociationInterceptor.html) interface provided in the Liberty server.

The trust association interface is a service provider API that enables the integration of third-party security services with a Liberty server. When processing the web request, the Liberty server calls out and passes the `HttpServletRequest` and `HttpServletResponse` to the trust association interceptors. The `HttpServletRequest` calls the `isTargetInterceptor` method of the interceptor to see whether the interceptor can process the request. After an appropriate trust association interceptor is selected, the `HttpServletRequest` is processed by the `negotiateValidateandEstablishTrust` method of the interceptor, and the result is returned in a `TAIResult` object. You can add your own logic code to each method of the custom TAI class.

> Note: The use of a TAI should be handled with care, where possible use a standard supported mechanism within Liberty to achieve security architecture and integration goals. For instance, the validation of a JWT can be achieved by using built-in process in Liberty stand-alone v16.0.0.3 and later versions.

This sample JWTTAI Java class provides the following functions:

* JWT validation based on the JWTConsumer API (Liberty JWT feature) - the JWT signature will be checked, and the claims (exp, iss, aud) will be validated against the expected values;

* Assign the ***Principal*** identity by retrieving the ***subject*** claim;

## How does it work?

When the Liberty server is launched, it will read the JWTConsumer configuration named `myJWTConsumer` and make it available. The JWTTAI TAI will also be initialized, but in this sample nothing is done during the TAI initialization process.

JWTTAI will only intercept ***HTTPS*** requests that contain a ***JWT in the `Authorization Bearer` header***.
It will create an instance of a *JWTBuilder* based on the `myJWTConsumer` configuration to validate and parse JWTs.

Once the JWTTAI is selected to handle the authentication, it will validate the JWT signature with the `trustedAlias` certificate taken from `myJWTConsumer` and will parse the JWT to retrieve the claims. 
The JWTConsumer only verifies the issuer, audience, expiry claims; to validate the other claims some lines of code need to be written in the `negotiateValidateandEstablishTrust` method.
If the JWT passes all the checks, the subject claim will be defined as the Principal identity and the request will be processed.
Otherwise the request will be rejected. 

Be careful, the subject claim needs to match an entry of the user registry.

This sample shows how a TAI can handle a simple JWT use case.

## Customization and compilation

To use this sample download the code or clone this repository and load the JWTTAI.java file into your favorite text editor or IDE.

Cutomize the JWTTAI.java class, if necessary. 

To compile the JWTTAI.java class and generate the JAR file, the only Java library required is **__WebSphere Liberty libraries__**, available with CICS Explorer SDK

Finally, upload the generated JAR file (as binary) to the appropriate zFS. In the example configuration, we have uploaded the JAR file in the *jars* folder (created manually) located in the server configuration directory (same directory as server.xml).

## Liberty configuration

To configure the Liberty server, add the following elements to the Liberty server.xml configuration:

```xml
<featureManager>
    <feature>cicsts:security-1.0</feature>
    <feature>jwt-1.0</feature>
</featureManager>

<library id="myTAIlib">
    <fileset dir="${server.config.dir}" includes="jwttai.jar"/>
</library>

<trustAssociation failOverToAppAuthType="false" id="myTrustAssociation" invokeForUnprotectedURI="false">
    <interceptors className="com.ibm.cicsdev.tai.JWTTAI" id="JWTTAI" invokeAfterSSO="true" invokeBeforeSSO="false" libraryRef="myTAIlib"/>
</trustAssociation>

<jwtConsumer audiences="zCEE" id="defaultJWTConsumer" issuer="idg" signatureAlgorithm="RS256" trustStoreRef="JWTStore" trustedAlias="DefaultCert.APIC"/>
<keyStore id="JWTStore" .../>

```

This modification adds the necessary JAR file to a library that can then be referenced by the `trustAssociation` element.
You may need to change the className attribute to match the name of your TAI class.
This also sets the failOverToAppAuthType attribute to false, so the application security is disabled.

More information on the `trustAssocation` and `interceptors` elements can be found on the [IBM Knowledge Center](https://www.ibm.com/support/knowledgecenter/en/SSEQTP_liberty/com.ibm.websphere.liberty.autogen.base.doc/ae/rwlp_config_trustAssociation.html).
More information on the `jwtConsumer` element can be found on the [IBM Knowledge Center](https://www.ibm.com/support/knowledgecenter/en/SSEQTP_liberty/com.ibm.websphere.liberty.autogen.base.doc/ae/rwlp_config_jwtConsumer.html).

## Testing the TAI

To test the TAI you will invoke an existing application hosted on your Liberty server. The invokation needs to be an HTTPS request containing a JWT in the Authorization header.
Make sure to use a JWT that contains the expected claims and to use the right set of public/private keys. If everything goes well you should see that the transaction is run with the user ID provided in the subject claim.
