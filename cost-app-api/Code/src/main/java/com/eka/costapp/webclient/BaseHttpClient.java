package com.eka.costapp.webclient;



import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.eka.costapp.error.ConnectError;
import com.eka.costapp.exception.ConnectException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;


@Component
@Qualifier("baseHttpClient")
public class BaseHttpClient
{
  protected RestTemplate restTemplate;
  
  public BaseHttpClient() {}
  
  @PostConstruct
  private void initialize()
  {
    restTemplate = new RestTemplate();
    HttpMessageConverter<Resource> resource = new ResourceHttpMessageConverter();
    FormHttpMessageConverter formHttpMessageConverter = new FormHttpMessageConverter();
    formHttpMessageConverter.addPartConverter(new MappingJackson2HttpMessageConverter(new CustomObjectMapper()));
    formHttpMessageConverter.addPartConverter(resource);
    
    List<HttpMessageConverter<?>> modifiedConverters = new ArrayList<HttpMessageConverter<?>>();
    modifiedConverters.addAll(restTemplate.getMessageConverters());
    modifiedConverters.add(0, formHttpMessageConverter);
    modifiedConverters.add(1, resource);
    
    restTemplate.setMessageConverters(modifiedConverters);
    restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
  }
  

  public <T, E> ResponseEntity<E> fireHttpRequest(URI uri, HttpMethod method, T requestBody, HttpHeaders headers, Class<E> responseEntityClass, String errorContext){
	  
	  try {
		  HttpEntity<T> request = new HttpEntity<T>(requestBody, headers);
		  return restTemplate.exchange(uri, method, request, responseEntityClass);
		}catch(HttpStatusCodeException httpException) {
			String error = CollectionUtils.isEmpty(httpException.getResponseHeaders().get("eka-connect-rest-api-error-response")) ? 
													httpException.getMessage() : 
													httpException.getResponseHeaders().get("eka-connect-rest-api-error-response").get(0);
			throw new ConnectException(createConnectError(httpException, errorContext), error);
		}
  }
 
  public <T, E> ResponseEntity<E> fireHttpRequest(URI uri, HttpMethod method, T requestBody, HttpHeaders headers, Class<E> responseEntityClass){
	  
	  HttpEntity<T> request = new HttpEntity<T>(requestBody, headers);
	  return restTemplate.exchange(uri, method, request, responseEntityClass);
  }
  
  private List<ConnectError> createConnectError(HttpStatusCodeException httpException, String errorContext) {
		
		ConnectError error = new ConnectError(String.valueOf(httpException.getStatusCode().value()), 
											 httpException.getResponseBodyAsString(), 
											 httpException.getLocalizedMessage(), 
											 errorContext);
		List<ConnectError> errors = new ArrayList<>();
		errors.add(error);
		return errors;
	}
}


@Component
class CustomObjectMapper extends ObjectMapper {
    private static final long serialVersionUID = 1L;

    public CustomObjectMapper() {
        this.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        this.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }
}
