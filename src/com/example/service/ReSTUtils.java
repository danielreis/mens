package com.example.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

/** @class ReSTUtils
*  @brief Implementa&ccedil;&atilde;o de m&eacute;todos para interagir com servi&ccedil;o ReST.
*  
*   Permite facilmente adicionar Headers e executar pedidos HTTP GET, PUT e POST
*/
public class ReSTUtils {

	private ArrayList <NameValuePair> params;	/**< par&acirc;metros do pedido */
	private ArrayList <NameValuePair> headers; /**<  Headers do pedido */

	private String url; /**< Url para efetuar pedido */

	private int responseCode; /**< C&oacute;digo da resposta do pedido */
	private String message;	/**< Mensagem de erro */

	private String response; /**< Resposta do servidor ReST ao pedido relaizado */

	/** Enumera&ccedil;&atilde;o de tipos de pedidos HTTP.
	 * 
	 * Tipos de pedidos permitidos: GET, PUT, POST
	 */
	public enum RequestMethod
	{
		GET,	/**< Pedido HTTP GET*/
		POST,	/**< Pedido HTTP POST*/
		PUT,	/**< Pedido HTTP PUT*/
	}
	
	
	/**
	 * Obt&eacute;m resposta do servidor
	 * @return reposta de servidor ReST
	 */
	public String getResponse() 
	{
		return response;
	}
	
	/**
	 * Obt&eacute;m mensagem de erro do servidor
	 * @return detalhes sobre erro em caso de erro no pedido
	 */
	public String getErrorMessage() 
	{
		return message;
	}

	/**
	 * Obt&eacute;m c&oacute;digo de resposta do servidor
	 * @return c&oacute;digo da reposta do servidor ReST
	 */
	public int getResponseCode() 
	{
		return responseCode;
	}

	/**
	 * Construtor do objeto
	 * @param url Url que define o destino do pedido
	 */
	public ReSTUtils(String url)
	{
		this.url = url;
		params = new ArrayList<NameValuePair>();
		headers = new ArrayList<NameValuePair>();
	}

	/**
	 * Adiciona par&acirc;metros ao pedido
	 * @param name nome do par&acirc;metro
	 * @param value valor que ele toma
	 */
	public void AddParam(String name, String value)
	{
		params.add(new BasicNameValuePair(name, value));
	}
	
	/**
	 * Adiciona headers ao pedido
	 * @param name nome do header
	 * @param value valor que ele toma
	 */
	public void AddHeader(String name, String value)
	{
		headers.add(new BasicNameValuePair(name, value));
	}


	/**
	 * Executa o m&eacute;todo passado em argumento
	 * @param method Tipo de opera&ccedil;&atilde;o HTTP
	 * @throws Exception Em caso de erro lan&ccedil;a excep&ccedil;&atilde;o
	 */
	public void Execute(RequestMethod method) throws Exception
	{
		switch(method) 
		{
			case GET:
			{
				//add parameters
				String combinedParams = "";
				if(!params.isEmpty()){
					combinedParams += "?";
					for(NameValuePair p : params)
					{
						String paramString = p.getName() + "=" + URLEncoder.encode(p.getValue(),"UTF-8");
						if(combinedParams.length() > 1)
						{
							combinedParams  +=  "&" + paramString;
						}
						else
						{
							combinedParams += paramString;
						}
					}
				}
	
				HttpGet request = new HttpGet(url + combinedParams);
	
				//add headers
				for(NameValuePair h : headers)
				{
					request.addHeader(h.getName(), h.getValue());
				}
	
				executeRequest(request, url);
				break;
			}
			case POST:
			{
				HttpPost request = new HttpPost(url);
	
				//add headers
				for(NameValuePair h : headers)
				{
					request.addHeader(h.getName(), h.getValue());
				}
	
				if(!params.isEmpty()){
					request.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
				}
	
				executeRequest(request, url);
				break;
			}
			case PUT:
			{
				HttpPut request = new HttpPut(url);
	
				//add headers
				for(NameValuePair h : headers)
				{
					request.addHeader(h.getName(), h.getValue());
				}
	
				if(!params.isEmpty()){
					request.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
				}
	
				executeRequest(request, url);
				break;
			}
		}
	}

	/**
	 * Executa o pedido HTTP
	 * @param request Pedido HTTP
	 * @param url Url de destino do pedido
	 */
	private void executeRequest(HttpUriRequest request, String url)
	{
		HttpClient client = new DefaultHttpClient();

		HttpResponse httpResponse;

		try {
			httpResponse = client.execute(request);
			responseCode = httpResponse.getStatusLine().getStatusCode();
			message = httpResponse.getStatusLine().getReasonPhrase();

			HttpEntity entity = httpResponse.getEntity();

			if (entity != null) 
			{

				InputStream instream = entity.getContent();
				response = convertStreamToString(instream);

				instream.close();
			}

		} catch (ClientProtocolException e)  
		{
			client.getConnectionManager().shutdown();
			e.printStackTrace();
		} catch (IOException e) 
		{
			client.getConnectionManager().shutdown();
			e.printStackTrace();
		}
	}

	/**
	 * Converte streams para strings
	 * @param is stream de entrada
	 * @return String correspondente ao stream de entrada
	 */
	private static String convertStreamToString(InputStream is) 
	{
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder sb = new StringBuilder();

		String line = null;
		try 
		{
			while ((line = reader.readLine()) != null) 
			{
				sb.append(line + "\n");
			}
		} catch (IOException e) 
		{
			e.printStackTrace();
		} 
		finally 
		{
			try 
			{
				is.close();
			} catch (IOException e) 
			{
				e.printStackTrace();
			}
		}
		return sb.toString();
	}
}