package com.mambu.apisdk.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mambu.apisdk.MambuAPIFactory;
import com.mambu.apisdk.exception.MambuApiException;

/**
 * Implementation of executing url requests with basic authorization
 * 
 * @author edanilkis
 * 
 */
@Singleton
public class RequestExecutorImpl implements RequestExecutor {

	private URLHelper urlHelper;
	private String encodedAuthorization;
	private final static String UTF8_charset = HTTP.UTF_8;
	private final static String wwwFormUrlEncodedContentType = "application/x-www-form-urlencoded; charset=UTF-8";

	// Added charset charset=UTF-8, MBU-4137 is now fixed
	private final static String jsonContentType = "application/json; charset=UTF-8";

	private final static String APPLICATION_KEY = APIData.APPLICATION_KEY; // as per JIRA issue MBU-3236

	private final static Logger LOGGER = Logger.getLogger(RequestExecutorImpl.class.getName());

	@Inject
	public RequestExecutorImpl(URLHelper urlHelper) {
		this.urlHelper = urlHelper;
	}

	// Without params and with default contentType (ContentType.WWW_FORM)
	@Override
	public String executeRequest(String urlString, Method method) throws MambuApiException {
		// invoke with default contentType (WWW_FORM)
		return executeRequest(urlString, null, method, ContentType.WWW_FORM);
	}

	// With params and with default contentType (ContentType.WWW_FORM)
	@Override
	public String executeRequest(String urlString, ParamsMap params, Method method) throws MambuApiException {
		// invoke with default contentType (WWW_FORM)
		return executeRequest(urlString, params, method, ContentType.WWW_FORM);
	}

	// With specifying the Content Type but without params
	/*
	 * Use this method to specify the requests's Content Type (must be used if content type is not WWW_FORM, for example
	 * for the json content type)
	 */
	@Override
	public String executeRequest(String urlString, Method method, ContentType contentTypeFormat)
			throws MambuApiException {
		// No params version
		return executeRequest(urlString, null, method, contentTypeFormat);
	}

	/*
	 * Use this method to specify the requests's Content Type (must be used if content type is not WWW_FORM, for example
	 * for the json content type)
	 */
	@Override
	public String executeRequest(String urlString, ParamsMap params, Method method, ContentType contentTypeFormat)
			throws MambuApiException {

		// Log API Request details
		logApiRequest(method, contentTypeFormat, urlString, params);

		// Add 'Application Key', if it was set by the application
		// Mambu may handle API requests differently for different Application Keys

		String applicationKey = MambuAPIFactory.getApplicationKey();
		if (applicationKey != null) {
			// add application key to the params map
			if (params == null) {
				params = new ParamsMap();
			}
			params.addParam(APPLICATION_KEY, applicationKey);

			// Log that Application key was added
			logAppKey(applicationKey);

		}

		String response = "";
		try {
			switch (method) {
			case GET:
				response = executeGetRequest(urlString, params);
				break;
			case POST:
				response = executePostRequest(urlString, params, contentTypeFormat);
				break;
			case PATCH:
				response = executePatchRequest(urlString, params);
				break;
			case DELETE:
				response = executeDeleteRequest(urlString, params);
				break;
			default:
				throw new IllegalArgumentException("Only methods GET, POST and DELETE are supported, not "
						+ method.name() + ".");
			}
		} catch (MalformedURLException e) {
			LOGGER.severe("MalformedURLException: " + e.getMessage());
			throw new MambuApiException(e);
		} catch (IOException e) {
			LOGGER.warning("IOException: message= " + e.getMessage());
			throw new MambuApiException(e);
		}
		return response;
	}

	/**
	 * Executes a POST request as per the interface specification
	 */
	private String executePostRequest(String urlString, ParamsMap params, ContentType contentTypeFormat)
			throws MalformedURLException, IOException, MambuApiException {

		// Get properly formatted ContentType
		final String contentType = getFormattedContentTypeString(contentTypeFormat);

		HttpParams httpParameters = new BasicHttpParams();
		HttpClient httpClient = new DefaultHttpClient(httpParameters);
		HttpPost httpPost = new HttpPost(urlString);
		httpPost.setHeader("Content-Type", contentType);
		httpPost.setHeader("Authorization", "Basic " + encodedAuthorization);

		if (params != null && params.size() > 0) {
			switch (contentTypeFormat) {

			case WWW_FORM:
				// convert parms to a list for HttpEntity
				List<NameValuePair> httpParams = getListFromParams(params);

				// use UTF-8 to encode
				HttpEntity postEntity = new UrlEncodedFormEntity(httpParams, UTF8_charset);

				httpPost.setEntity(postEntity);

				break;

			case JSON:
				// Make jsonEntity
				StringEntity jsonEntity = makeJsonEntity(params);

				httpPost.setEntity(jsonEntity);

				break;
			}
		}

		// execute
		HttpResponse httpResponse = httpClient.execute(httpPost);

		// Process response
		String response = processResponse(httpResponse, urlString);

		return response;

	}

	/**
	 * Executes a PATCH request as per the interface specification
	 */
	private String executePatchRequest(String urlString, ParamsMap params) throws MalformedURLException, IOException,
			MambuApiException {

		// PATCH request is using json ContentType
		final String contentType = jsonContentType;

		HttpParams httpParameters = new BasicHttpParams();
		HttpClient httpClient = new DefaultHttpClient(httpParameters);

		// HttpPatch is available since org.apache.httpcomponents v4.2
		HttpPatch httpPatch = new HttpPatch(urlString);
		httpPatch.setHeader("Content-Type", contentType);
		httpPatch.setHeader("Authorization", "Basic " + encodedAuthorization);

		// Format jsonEntity
		StringEntity jsonEntity = makeJsonEntity(params);

		httpPatch.setEntity(jsonEntity);

		// execute
		HttpResponse httpResponse = httpClient.execute(httpPatch);

		// Process response
		String response = processResponse(httpResponse, urlString);

		return response;

	}

	/***
	 * Execute a GET request as per the interface specification
	 * 
	 * @param urlString
	 */
	private String executeGetRequest(String urlString, ParamsMap params) throws MalformedURLException, IOException,
			MambuApiException {

		if (params != null && params.size() > 0) {
			urlString = new String((urlHelper.createUrlWithParams(urlString, params)));
		}

		HttpParams httpParameters = new BasicHttpParams();

		HttpClient httpClient = new DefaultHttpClient(httpParameters);

		HttpGet httpGet = new HttpGet(urlString);
		// add Authorozation header
		httpGet.setHeader("Authorization", "Basic " + encodedAuthorization);
		// setHeader("Content-Type") not need for GET requests

		// execute
		HttpResponse httpResponse = httpClient.execute(httpGet);

		// Process response
		String response = processResponse(httpResponse, urlString);

		return response;
	}

	/***
	 * Execute a DELETE request as per the interface specification
	 * 
	 * @param urlString
	 * 
	 * @param params
	 *            ParamsMap with parameters
	 */
	private String executeDeleteRequest(String urlString, ParamsMap params) throws MalformedURLException, IOException,
			MambuApiException {

		if (params != null && params.size() > 0) {
			urlString = new String((urlHelper.createUrlWithParams(urlString, params)));
		}

		HttpParams httpParameters = new BasicHttpParams();

		HttpClient httpClient = new DefaultHttpClient(httpParameters);

		HttpDelete httpDelete = new HttpDelete(urlString);
		httpDelete.setHeader("Authorization", "Basic " + encodedAuthorization);

		// execute
		HttpResponse httpResponse = httpClient.execute(httpDelete);

		// Process response
		String response = processResponse(httpResponse, urlString);

		return response;
	}

	/**
	 * Make StringEntity for HTTP requests from the JSON string supplied in the ParamsMap
	 * 
	 * @param params
	 *            ParamsMap with JSON string
	 */
	private StringEntity makeJsonEntity(ParamsMap params) throws UnsupportedEncodingException {

		if (params == null) {
			throw new IllegalArgumentException("JSON requests require non NULL ParamsMap with JSON string");
		}
		// Parameter (json string) is expected as JSON_OBJECT parameter
		String jsonString = params.get(APIData.JSON_OBJECT);

		if (jsonString == null) {
			throw new IllegalArgumentException("JSON string cannot be NULL");
		}

		// Add APPKEY to jsonString (see MBU-3892, implemented in 3.3 release)
		jsonString = addAppKeyToJson(jsonString, params);

		// Format jsonEntity
		StringEntity jsonEntity = new StringEntity(jsonString, UTF8_charset);

		return jsonEntity;

	}

	/**
	 * Process and return the response to an HTTP request. Throw MambuApiException if request failed
	 * 
	 * @param httpResponse
	 *            HTTP response
	 * @param urlString
	 *            URL string for the HTTP request
	 * @return HTTP response string
	 */
	private String processResponse(HttpResponse httpResponse, String urlString) throws IOException, MambuApiException {

		// get status
		int status = httpResponse.getStatusLine().getStatusCode();

		InputStream content = null;
		String response = "";

		// Get the response Entity
		HttpEntity entity = httpResponse.getEntity();

		if (entity != null) {
			content = entity.getContent();
			if (content != null) {
				response = readStream(content);
			}
		}

		// Log Mambu response
		logApiResponse(status, response);

		// if status is Ok - return the response
		if (status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_CREATED) {
			return response;
		}

		// Set error code and throw Mambu Exception
		Integer errorCode = status;

		// Log raising exception
		if (LOGGER.isLoggable(Level.WARNING)) {
			LOGGER.warning("Creating exception, error code=" + errorCode + " for url=" + urlString);
		}
		// pass to MambuApiException the content that goes with the error code
		throw new MambuApiException(errorCode, response);

	}

	/**
	 * Reads a stream into a String
	 * 
	 * @param content
	 * 
	 * @return
	 * 
	 * @throws IOException
	 */
	private String readStream(InputStream content) throws IOException {

		String response = "";

		// read the response content
		BufferedReader in = new BufferedReader(new InputStreamReader(content, UTF8_charset));
		String line;
		while ((line = in.readLine()) != null) {
			response += line;
		}
		return response;
	}

	@Override
	public void setAuthorization(String username, String password) {
		// encode the username and password
		String userNamePassword = username + ":" + password;
		encodedAuthorization = new String(Base64.encodeBase64(userNamePassword.getBytes()));

	}

	/**
	 * Convert Params Map into a List<NameValuePair> for HttpPpost
	 * 
	 * @param params
	 * 
	 * @return List<NameValuePair>
	 * 
	 * @throws
	 */
	private List<NameValuePair> getListFromParams(ParamsMap params) {

		List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(params.size());

		for (Map.Entry<String, String> entry : params.entrySet()) {
			// only put the parameter in the URL if its value is not null
			if (entry.getValue() != null) {
				nameValuePairs.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));

			}
		}
		return nameValuePairs;
	}

	/**
	 * Get the formatted content type string for the content type enum value
	 */
	private String getFormattedContentTypeString(ContentType contentTypeFormat) {
		switch (contentTypeFormat) {
		case WWW_FORM:
			return wwwFormUrlEncodedContentType;
		case JSON:
			return jsonContentType;
		default:
			return wwwFormUrlEncodedContentType;
		}
	}

	/**
	 * Add json formatted appKey value to the original json string
	 * 
	 * @param jsonString
	 *            original json string
	 * 
	 * @param params
	 *            the ParamsMap containing the appKey value (optionally)
	 * 
	 * @return jsonStringWithAppKey json string with appKey added
	 */
	private String addAppKeyToJson(String jsonString, ParamsMap params) {

		if (params == null) {
			return jsonString;
		}

		String appKey = params.get(APPLICATION_KEY);
		if (appKey == null || appKey.length() == 0) {
			return jsonString;
		}

		// First compile the following string: "{appKey":"appKeyValue",
		// This formatted appKey string will be appended with the original json string (without the first '{')

		String appKeyString = "{\"" + APPLICATION_KEY + "\":\"" + appKey + "\",";

		// Check if we have the string to insert into
		if (jsonString == null || jsonString.length() == 0) {
			// Nothing to insert into. Return just the appKey param (surrounded by the square brackets)
			return appKeyString.replace(',', '}');
		}

		// We need input json string without the first '{'
		String jsonStringToAdd = jsonString.substring(1);

		// Create initial String Buffer large enough to hold the resulting two strings
		StringBuffer jsonWithAppKey = new StringBuffer(jsonStringToAdd.length() + appKeyString.length());

		// Append the appkey and the the json string
		jsonWithAppKey.append(appKeyString);
		jsonWithAppKey.append(jsonStringToAdd);

		return jsonWithAppKey.toString();

	}

	/**
	 * Log API request details. This is a helper method for using consistent formating when using Java Logger to print
	 * the details of the API request
	 * 
	 * @param method
	 *            request's method
	 * @param contentType
	 *            request's content type
	 * @param urlString
	 *            request's url
	 * @param params
	 *            the ParamsMap.
	 * 
	 *            The method shall be invoked before the appKey is added to the map to avoid printing appKey details
	 * 
	 */
	private void logApiRequest(Method method, ContentType contentType, String urlString, ParamsMap params) {

		if (!LOGGER.isLoggable(Level.INFO) || method == null) {
			return;
		}

		// Log Method and URL.
		// Log params if applicable
		// Log Json for Json requests
		String logDetails = "\n" + method.name() + " with URL=";
		String jsonString = null;
		String urlWithParams = null;
		switch (method) {
		case GET:
			// For GET add params to the url as in to be sent request itself
			urlWithParams = new String((urlHelper.createUrlWithParams(urlString, params)));
			logDetails = logDetails + urlWithParams;
			break;

		case POST:
		case PATCH:
			switch (contentType) {
			case WWW_FORM:
				// Log URL and params as separate items
				logDetails = logDetails + urlString;
				if (params != null) {
					String postParams = params.getURLString();
					logDetails = logDetails + "\nParams=" + postParams;
				}
				break;
			case JSON:
				// Log URL and Json string
				logDetails = logDetails + urlString;
				if (params != null) {
					jsonString = params.get(APIData.JSON_OBJECT);
				}
				break;
			}

			break;
		case DELETE:
			// For DELETE ads params to the url as in to be sent request itself
			urlWithParams = new String((urlHelper.createUrlWithParams(urlString, params)));
			logDetails = logDetails + urlWithParams;
			break;

		default:
			break;

		}
		// Add content type to logging, if not NULL
		if (contentType != null) {
			logDetails = logDetails + " (contentType=" + contentType + ")";
		}

		// Now we can Log URL and Params
		LOGGER.info(logDetails);
		// For Jsons - log the Json string
		if (jsonString != null) {
			logJsonInput(jsonString);
		}

	}

	/**
	 * Log Json string details. This is a helper method for modifying the original Json string to remove details that
	 * are needed for logging (for example, encoded data when sending documents via Json)
	 * 
	 * @param jsonString
	 *            json string in the API request
	 * 
	 */
	private void logJsonInput(String jsonString) {

		if (!LOGGER.isLoggable(Level.INFO) || jsonString == null) {
			return;
		}

		// handle some special cases to have user friendly output
		// Handle jsons with encoded documents. Encoded data is of no use for logging. Strip it out

		// Documents API case - remove base64 encoding
		// Find the documentContent tag (containing base64 string) and remove extra content
		final String documentRoot = "{\"document\":";
		if (jsonString.startsWith(documentRoot)) {
			final String documentContentParam = "\"documentContent\":";
			int contentStarts = jsonString.indexOf(documentContentParam);
			if (contentStarts != -1) {
				// Get everything up to the documentContent plus some more
				final int encodedCharsToShow = 20;
				// Also add "..." to indicate that the output was truncated
				jsonString = jsonString
						.substring(0, contentStarts + documentContentParam.length() + encodedCharsToShow) + "...\"}";

			}
		}

		LOGGER.info("Input JsonString=" + jsonString);

	}

	/**
	 * Log API response details. This is a helper method for using consistent formating when using Java Logger to print
	 * the details of the API response
	 * 
	 * @param status
	 *            response status
	 * @param response
	 *            response string
	 */
	private void logApiResponse(int status, String response) {

		if (!LOGGER.isLoggable(Level.INFO)) {
			return;
		}
		// Log response details
		if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_CREATED) {
			// Error status. Log as error
			LOGGER.info("Error status=" + status + " Error response=" + response);
		} else {
			// Log success Response.
			// Handle special cases where response contains encoded strings which we don't need to see in the logger
			// (for example, base64 encoded data when getting images and files)
			// Find ";base64,";
			final String encodedDataIndicator = APIData.BASE64_ENCODING_INDICATOR;
			final int encodedDataStart = response.indexOf(encodedDataIndicator);
			if (encodedDataStart != -1) {
				// This is a response containing base64 encoded data. Strip the bulk of it out
				final int howManyEncodedToShow = 20;
				int totalCharsToShow = encodedDataStart + encodedDataIndicator.length() + howManyEncodedToShow;
				// Get the needed part of this response and add "..." indicator
				response = response.substring(0, totalCharsToShow) + "...\"";
			}

			// Log API response Status and the Response string
			LOGGER.info("\nResponse Status=" + status + "\nResponse message=" + response);
		}

	}

	/**
	 * Log Application Key details. This is a helper method for logging partial application key details to indicate the
	 * that application key is used when building the API request
	 * 
	 * @param applicationKey
	 *            Application Key string
	 */
	private void logAppKey(String applicationKey) {

		if (!LOGGER.isLoggable(Level.INFO)) {
			return;
		}
		final int keyLength = applicationKey.length();
		final int printLength = 3;
		// Mambu App Keys are very long but just to prevent any errors need to ensure there is enough to print
		if (keyLength >= printLength) {
			LOGGER.info("Added Application key=" + applicationKey.substring(0, printLength) + "..."
					+ applicationKey.substring(keyLength - printLength, keyLength));
		}

	}
}
