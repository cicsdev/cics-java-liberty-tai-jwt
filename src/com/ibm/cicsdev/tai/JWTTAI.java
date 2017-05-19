/* Licensed Materials - Property of IBM                                   */
/*                                                                        */
/* SAMPLE                                                                 */
/*                                                                        */
/* (c) Copyright IBM Corp. 2017 All Rights Reserved                       */
/*                                                                        */
/* US Government Users Restricted Rights - Use, duplication or disclosure */
/* restricted by GSA ADP Schedule Contract with IBM Corp                  */
/*                                                                        */
package com.ibm.cicsdev.tai;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// JWT-related classes
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

//TAI and RACF 
import com.ibm.websphere.security.WebTrustAssociationException;
import com.ibm.websphere.security.WebTrustAssociationFailedException;
import com.ibm.wsspi.security.tai.TrustAssociationInterceptor;
import com.ibm.wsspi.security.tai.TAIResult;
import com.ibm.crypto.provider.RACFInputStream;

public class JWTTAI implements TrustAssociationInterceptor {

	private boolean isInitialized = false;
	private PublicKey publicKey = null;
	   
	   public JWTTAI() {
	      super();
	   }

	/*
	 * @see com.ibm.wsspi.security.tai.TrustAssociationInterceptor#isTargetInterceptor
	 * (javax.servlet.http.HttpServletRequest)
	 */
	   public boolean isTargetInterceptor(HttpServletRequest req)
	                  throws WebTrustAssociationException {
		   //Add logic to determine whether to intercept this request, i.e. this TAI is going to authenticate the user
		   
		   //If the interceptor hasn't been correctly initialized, it can't intercept the request
		   if (!isInitialized) {
			   System.out.println("[JWTTAI] TAI interceptor not correctly initialized");
			   return false;
		   }
		   boolean match = false;
	       //Verify that the Authorization header contains a JWT
		   if (req.getHeader("Authorization")!=null) {
			   System.out.println("[JWTTAI] HTTP header Authorization = " + req.getHeader("Authorization"));
			   match = req.getHeader("Authorization").matches("Bearer ([a-zA-Z0-9]|-|_)+\\.([a-zA-Z0-9]|-|_)+\\.([a-zA-Z0-9]|-|_|=)+");
			   System.out.println("[JWTTAI] HTTP header Authorization match = " + match);
		   }
		   //If request is using HTTPS and does contain a JWT then intercept
		   if (req.isSecure() && match){
			   return true;
		   }
	      return false;
	   }

	/*
	 * @see com.ibm.wsspi.security.tai.TrustAssociationInterceptor#negotiateValidateandEstablishTrust
	 * (javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)
	 */
	   public TAIResult negotiateValidateandEstablishTrust(HttpServletRequest req,
	                    HttpServletResponse resp) throws WebTrustAssociationFailedException {
	        //Add logic to authenticate an user and return a TAI result.
	        
		    String tai_user = "";
	        String jwt = req.getHeader("Authorization").split(" ")[1];
	        try {
	        	//Validate the JWT signature, issuer, validity and Retrieve the claims
	        	Claims claims = Jwts.parser().setSigningKey(publicKey).requireIssuer("idg").parseClaimsJws(jwt).getBody();
	        	tai_user = claims.getSubject();
	        	System.out.println("[JWTTAI] TAI USER = " + tai_user);
	        } catch (Exception e) {
	        	//If the JWT can't be validated then an exception will be thrown, thus the user is unauthenticated (401)
	        	System.out.println("[JWTTAI] " + e.getMessage());
	        	return TAIResult.create(HttpServletResponse.SC_UNAUTHORIZED);
	        }
	        //The user has been authenticated and the identity is tai_user
	        return TAIResult.create(HttpServletResponse.SC_OK, tai_user);
	    }

	/*
	 * @see com.ibm.wsspi.security.tai.TrustAssociationInterceptor#initialize(java.util.Properties)
	 */
	    public int initialize(Properties arg0)
	                    throws WebTrustAssociationFailedException {
	    	//Initialize the TAI: retrieve properties and load the public key used for validation
	    	String type = arg0.getProperty("type", "JKS");
	    	String location = arg0.getProperty("location", "key.jks");
	    	char[] password = arg0.getProperty("password", "Liberty").toCharArray();
	    	String alias = arg0.getProperty("alias", "default");
	    	
	        System.out.println("[JWTTAI] PARMS = " + type + " " + location + " " + alias);
	    	try {
	    		InputStream is;
	    		//For JCERACFKS
		        if (type.contains("JCERACFKS")){
		            //password can apparently be anything
		            is = new RACFInputStream("<RING_OWNER>",location, password);
		            KeyStore keystore = KeyStore.getInstance(type);
		            keystore.load(is, password);
		            is.close();
		            Certificate cert = keystore.getCertificate(alias);
		            if (cert==null) {
		            	System.out.println("[JWTTAI] Alias not found");
		            	return 0;
		            }
		            publicKey = cert.getPublicKey();
		            isInitialized = true;
		        //For jks
		        } else {
		            //The jks should be in the server resources/security folder
		            is = new FileInputStream("<PATH_TO_LIBERTY_SERVER>/resources/security/" + location);
		            KeyStore keystore = KeyStore.getInstance(type);
		            keystore.load(is, password);
		            is.close();
		            Key key = keystore.getKey(alias, password);
		            if (key==null) {
		            	System.out.println("[JWTTAI] Alias not found");
		            } else if (key instanceof PrivateKey) {
		            	System.out.println("[JWTTAI] PrivateKey");
		            	// Get certificate of public key
		            	Certificate cert = keystore.getCertificate(alias);
		              	// Get public key
		              	publicKey = cert.getPublicKey();
		              	isInitialized = true;
		            } else if (key instanceof PublicKey) {
		            	System.out.println("[JWTTAI] PublicKey");
		            	publicKey = (PublicKey) key;
		            	isInitialized = true;
		            }
		        }

	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	        return 0;
	    }

	/*
	 * @see com.ibm.wsspi.security.tai.TrustAssociationInterceptor#getVersion()
	 */
	    public String getVersion() {
	        return "JWTTAI-1.0";
	    }

	/*
	 * @see com.ibm.wsspi.security.tai.TrustAssociationInterceptor#getType()
	 */
	    public String getType() {
	        return this.getClass().getName();
	    }

	/*
	 * @see com.ibm.wsspi.security.tai.TrustAssociationInterceptor#cleanup()
	 */
	    public void cleanup()

	    {}
}
