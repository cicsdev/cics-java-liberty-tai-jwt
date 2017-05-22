# cics-java-liberty-tai-jwt
Sample WebSphere Liberty Trust Association Interceptor (TAI) for use with CICS Liberty to validate [JSON web tokens (JWTs)](https://tools.ietf.org/html/rfc7519)
and set the authorized user ID based on the JWT subject claim.

For detailed configuration instructions see [Configuring TAI in Liberty](https://www.ibm.com/support/knowledgecenter/en/SSEQTP_liberty/com.ibm.websphere.wlp.nd.multiplatform.doc/ae/twlp_sec_tai.html) and [Developing a custom TAI for Liberty](https://www.ibm.com/support/knowledgecenter/en/SSEQTP_liberty/com.ibm.websphere.wlp.nd.multiplatform.doc/ae/twlp_dev_custom_tai.html).

## Introduction

You can configure Liberty to integrate with a third-party security services by using a TAI. The TAI can be called before or after single sign-on (SSO).
You can develop a custom TAI class by implementing the [com.ibm.wsspi.security.tai.TrustAssociationInterceptor](https://www.ibm.com/support/knowledgecenter/en/SSEQTP_8.5.5/com.ibm.websphere.javadoc.doc/web/spidocs/com/ibm/wsspi/security/tai/TrustAssociationInterceptor.html) interface provided in the Liberty server.

The trust association interface is a service provider API that enables the integration of third-party security services with a Liberty server. When processing the web request, the Liberty server calls out and passes the `HttpServletRequest` and `HttpServletResponse` to the trust association interceptors. The `HttpServletRequest` calls the `isTargetInterceptor` method of the interceptor to see whether the interceptor can process the request. After an appropriate trust association interceptor is selected, the `HttpServletRequest` is processed by the `negotiateValidateandEstablishTrust` method of the interceptor, and the result is returned in a `TAIResult` object. You can add your own logic code to each method of the custom TAI class.

> Note: The use of a TAI should be handled with care, where possible use a standard supported mechanism within Liberty to achieve security architecture and integration goals. For instance, the validation of a JWT can be achieved by using built-in process in Liberty stand-alone v16.0.0.3 and later versions.

This sample JWTTAI Java class provides the following function:

* Validating JWTs - Standard validations like expiry date and signature, but also custom ones like checking the ***issuer*** claim against expected values;

* Assigning the ***Principal*** identity by retrieving the ***subject*** claim and identifying it as the userid to run the request with;

## How does it work?

At server initialization, the JWTTAI reads the properties that have been defined for it in server.xml. These properties refer to the location and alias of the public key to load in order to validate the JWTs signature. They provide the flexibility to change the key reference without having to modify the JWTTAI code. If the JWTTAI is not correctly initialized, it won't intercept incoming requests. The table below shows the properties that can be defined. 

|Property  |Default Value |Description                                                                                                                                 |
|:--------:|:------------:|:------------------------------------------------------------------------------------------------------------------------------------------ |
|type      |JKS           | Keystore type, accepted values are JKS and JCERACFKS                                                                                       |
|location  |key.jks       | Name of the keystore. If type=JKS, then the jks file must be in *resources/security*. If type=JCERACFKS, then specify the SAF Keyring name |
|password  |Liberty       | Keystore password, not relevant for SAF Keyring                                                                                            |
|alias     |default       | Key or certificate alias/label                                                                                                             |

JWTTAI will only intercept ***HTTPS*** requests that contain a ***JWT in the `Authorization Bearer` header***.

The JWTTAI uses the open-source library [io.jsonwebtoken](https://github.com/jwtk/jjwt) to validate and parse JWTs.

Once the JWTTAI is selected to handle the authentication, it will validate the JWT signature with the public key loaded at initialization and will parse the JWT to retrieve the claims. 
This sample only verifies the issuer claim, but the other claims can also be verified.
If the JWT passes all the checkings, the subject claim will be defined as the Principal identity and the request will be processed.
Otherwise the request will be rejected. 

Be careful, the subject claim needs to match an entry of the user registry.

This sample shows how a TAI can handle a simple JWT use case.

## Customization and compilation

To use this sample download the code or clone this repository and load the JWTTAI.java file into your preferred Java editor or IDE.

Cutomize the JWTTAI.java class, and change at least one of the following values: `<RING_OWNER>`, `<PATH_TO_LIBERTY_SERVER>`.

To compile the JWTTAI.java class and generate the JAR file, three Java libraries are required: 

* WebSphere Liberty libraries, available with CICS Explorer SDK 
* ibmjceprovider.jar, available in the Java SDK for z/OS at $JAVA_HOME/lib/ext
* io.jsonwebtoken (i.e. jjwt-x.y.z.jar), available from [github](https://github.com/jwtk/jjwt)

Finally, upload the generated JAR file (as binary) to zFS. In the example configuration we put this in the server configuration directory (same directory as server.xml). You will also need to upload the jjwt-x.y.z.jar and its jackson-**.jar dependencies to zFS, so that they can be added to your Liberty server library.

## Liberty configuration

To configure the Liberty server, add the following elements to the Liberty server.xml configuration:

```xml
<featureManager>
    <feature>cicsts:security-1.0</feature>
</featureManager>

<library id="myTAIlib">
    <fileset dir="${server.config.dir}" includes="jackson-databind-2.8.2.jar"/>
    <fileset dir="${server.config.dir}" includes="jackson-core-2.8.2.jar"/>
    <fileset dir="${server.config.dir}" includes="jackson-annotations-2.8.0.jar"/>
    <fileset dir="${server.config.dir}" includes="jjwt-0.7.0.jar"/>
    <fileset dir="${server.config.dir}" includes="jwttai.jar"/>
</library>

<trustAssociation failOverToAppAuthType="false" id="myTrustAssociation" invokeForUnprotectedURI="false">
    <interceptors className="com.ibm.cicsdev.tai.JWTTAI" id="JWTTAI" invokeAfterSSO="true" invokeBeforeSSO="true" libraryRef="myTAIlib">
        <!--<properties alias="jwtapicsign" location="key.jks" password="password" type="JKS"/>-->
	<properties alias="DefaultCert.APIC" location="Keyring.APIC" password="password" type="JCERACFKS"/>
    </interceptors>
</trustAssociation>

```

This modification adds the necessary JAR files to a library that can then be referenced by the `trustAssociation` element.
You may need to change the className attribute to match the name of your TAI class.
This also sets the failOverToAppAuthType attribute to false, so app security is disabled.

More information on the `trustAssocation` and `interceptors` elements can be found on the [IBM Knowledge Center](https://www.ibm.com/support/knowledgecenter/en/SSEQTP_liberty/com.ibm.websphere.liberty.autogen.base.doc/ae/rwlp_config_trustAssociation.html)

## Testing the TAI

To test the TAI you will need to call into an existing application hosted in your Liberty JVM server. When you call the application, do so by sending a HTTPS request containing a JWT in the Authorization header.
Make sure to use a JWT that contains the expected claims and to use the right set of public/private keys. If everything goes well you should see that the transaction is run with the user ID provided in the subject claim.
