package my.company.olingo.oData;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.olingo.odata2.api.commons.HttpStatusCodes;
import org.apache.olingo.odata2.api.edm.Edm;
import org.apache.olingo.odata2.api.edm.EdmAnnotatable;
import org.apache.olingo.odata2.api.edm.EdmAnnotationAttribute;
import org.apache.olingo.odata2.api.edm.EdmAnnotationElement;
import org.apache.olingo.odata2.api.edm.EdmElement;
import org.apache.olingo.odata2.api.edm.EdmEntitySet;
import org.apache.olingo.odata2.api.edm.EdmEntityType;
import org.apache.olingo.odata2.api.edm.EdmException;
import org.apache.olingo.odata2.api.edm.EdmTyped;
import org.apache.olingo.odata2.api.ep.EntityProvider;
import org.apache.olingo.odata2.api.exception.ODataException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.sap.core.connectivity.api.configuration.ConnectivityConfiguration;
import com.sap.core.connectivity.api.configuration.DestinationConfiguration;

public class SFoDataConnect {

	public static final String	HTTP_HEADER_ACCEPT			= "Accept";
	public static final String	HTTP_METHOD_PUT				= "PUT";
	public static final String	HTTP_METHOD_POST			= "POST";
	public static final String	HTTP_METHOD_GET				= "GET";
	public static final String	METADATA					= "$metadata";
	public static final String	HTTP_HEADER_CONTENT_TYPE	= "Content-Type";
	public static final String	APPLICATION_XML				= "application/xml";
	public static final String	SEPARATOR					= "/";
	public static final boolean	PRINT_RAW_CONTENT			= true;

	String						destination					= "";
	String						userPassword				= "";

	DestinationConfiguration	destConfiguration			= null;

	public SFoDataConnect() {
	}

	@SuppressWarnings("restriction")
	public void initConnectionSuccessFactors() throws NamingException, IOException, ODataException {
		Context context = new InitialContext();

		// Get the connectivity settings from the context
		ConnectivityConfiguration configuration = (ConnectivityConfiguration) context.lookup("java:comp/env/connectivityConfiguration");
		this.destConfiguration = configuration.getConfiguration("sap_hcmcloud_core_odata");

		// Create user/password token + Authentication
		String userPassword = destConfiguration.getProperty("User") + ":" + destConfiguration.getProperty("Password");
		this.userPassword = "Basic " + new sun.misc.BASE64Encoder().encode(userPassword.getBytes());
	}

	@SuppressWarnings("unchecked")
	public JSONObject getMetaData() throws IOException, ODataException {
		// Create the JSON Object for returning the data

		JSONObject jsonMetaData = new JSONObject();
		JSONArray jsonEntities = new JSONArray();

		// Construct the URL for the metadata
		String urlMetadata = destConfiguration.getProperty("URL") + SEPARATOR + METADATA;
		URL url = new URL(urlMetadata);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod(HTTP_METHOD_GET);
		connection.setRequestProperty("Authorization", this.userPassword);
		connection.setRequestProperty(HTTP_HEADER_ACCEPT, APPLICATION_XML);
		connection.connect();
		checkStatus(connection);

		// Get the metadata from the server
		Edm edm = EntityProvider.readMetadata(connection.getInputStream(), false);
		connection.disconnect(); // get some memory back

		// Go through all entities
		// ---------------------------------------------------------------
		for (EdmEntitySet entity : edm.getEntitySets()) {

			if (!entity.getName().startsWith("PerPerson"))
				continue;

			JSONObject jsonEntity = new JSONObject();

			// Get the fields within this entity
			jsonEntity.put("properties", loadInformation(entity.getEntityType(), entity.getEntityType().getPropertyNames(), true));

			// Get the navigations for this entity
			jsonEntity.put("navigation", loadInformation(entity.getEntityType(), entity.getEntityType().getNavigationPropertyNames(), false));

			// Get info about the entity
			loadAnnotations(entity.getEntityType().getAnnotations().getAnnotationElements(), jsonEntity);
			jsonEntity.put("name", entity.getName());

			jsonEntities.add(jsonEntity);
		}

		jsonMetaData.put("Entities", jsonEntities);

		return jsonMetaData;
	}

	private HttpStatusCodes checkStatus(HttpURLConnection connection) throws IOException {
		// Check everything is OK from the server side
		HttpStatusCodes httpStatusCode = HttpStatusCodes.fromStatusCode(connection.getResponseCode());
		if (400 <= httpStatusCode.getStatusCode() && httpStatusCode.getStatusCode() <= 599) {
			throw new RuntimeException("Http Connection failed with status " + httpStatusCode.getStatusCode() + " " + httpStatusCode.toString());
		}
		return httpStatusCode;
	}

	@SuppressWarnings("unchecked")
	private void loadAnnotations(List<EdmAnnotationElement> annotElements, JSONObject Json) throws EdmException {
		// Loop through all annotations and return the name and text
		for (EdmAnnotationElement annotElem : annotElements) {
			List<EdmAnnotationAttribute> annotAttributes = annotElem.getAttributes();
			List<EdmAnnotationElement> annotChildren = annotElem.getChildElements();

			for (int i = 0; i < annotChildren.size(); i++) {
				String annotText = annotChildren.get(i).getText();
				String annotName = annotAttributes.get(i).getText();
				Json.put(annotName, annotText);
			}

		}
	}

	@SuppressWarnings("unchecked")
	private void loadSFAttributes(List<EdmAnnotationAttribute> annotAttributes, JSONObject Json) throws EdmException {
		if (annotAttributes == null)
			return;

		// Loop through all annotations and return the name and text
		for (EdmAnnotationAttribute annotAttribute : annotAttributes) {
			Json.put(annotAttribute.getName(), annotAttribute.getText());
		}
	}

	@SuppressWarnings("unchecked")
	private JSONArray loadInformation(EdmEntityType entityType, List<String> propertyNames, Boolean isElement) throws EdmException {
		JSONArray jsonProperties = new JSONArray();

		// Get the list of keys (for checking later if property is key)
		List<String> keys = entityType.getKeyPropertyNames();

		// Go through all properties and get some info
		for (String propertyName : propertyNames) {
			EdmTyped property = entityType.getProperty(propertyName);

			JSONObject jsonProperty = new JSONObject();

			// Get basic info
			jsonProperty.put("name", property.getName());
			jsonProperty.put("type", property.getType().getName());

			if (isElement) {
				jsonProperty.put("maxLength", ((EdmElement) property).getFacets().getMaxLength());
				jsonProperty.put("isKey", keys.contains((String) propertyName));
			} else {
				jsonProperty.put("multiplicity", property.getMultiplicity().toString());
			}

			// Get all the SF specific attributes
			loadSFAttributes(((EdmAnnotatable) property).getAnnotations().getAnnotationAttributes(), jsonProperty);

			// return all annotations
			loadAnnotations(((EdmAnnotatable) property).getAnnotations().getAnnotationElements(), jsonProperty);

			jsonProperties.add(jsonProperty);
		}

		return jsonProperties;
	}

}
