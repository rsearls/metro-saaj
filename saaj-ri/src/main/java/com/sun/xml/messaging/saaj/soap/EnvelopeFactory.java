/*
 * Copyright (c) 1997, 2021 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.messaging.saaj.soap;

import com.sun.xml.messaging.saaj.LazyEnvelopeSource;
import com.sun.xml.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.messaging.saaj.util.JAXMStreamSource;
import com.sun.xml.messaging.saaj.util.LogDomainConstants;
import com.sun.xml.messaging.saaj.util.ParserPool;
import com.sun.xml.messaging.saaj.util.RejectDoctypeSaxFilter;
import com.sun.xml.messaging.saaj.util.transform.EfficientStreamingTransformer;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParser;
import jakarta.xml.soap.SOAPException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Logger;

/**
 * EnvelopeFactory creates SOAP Envelope objects using different
 * underlying implementations.
 */
public class EnvelopeFactory {
    private static final String SAX_PARSER_POOL_SIZE_PROP_NAME = "com.sun.xml.messaging.saaj.soap.saxParserPoolSize";
    private static final int DEFAULT_SAX_PARSER_POOL_SIZE = 5;
    
    protected static final Logger
        log = Logger.getLogger(LogDomainConstants.SOAP_DOMAIN,
        "com.sun.xml.messaging.saaj.soap.LocalStrings");

    private static ContextClassloaderLocal<ParserPool> parserPool =
            new ContextClassloaderLocal<ParserPool>() {
                @Override
                protected ParserPool initialValue() throws Exception {
                	Integer poolSize = AccessController.doPrivileged(
                			new PrivilegedAction<Integer>() {
                				@Override
                				public Integer run() {
                					try {
                						return Integer.getInteger(
                								SAX_PARSER_POOL_SIZE_PROP_NAME, 
                								DEFAULT_SAX_PARSER_POOL_SIZE);
                					} catch (SecurityException se) {
                						return DEFAULT_SAX_PARSER_POOL_SIZE;
                					}
                				}
                			});
                    return new ParserPool(poolSize);
                }
    };

    public static Envelope createEnvelope(Source src, SOAPPartImpl soapPart)
        throws SOAPException 
    {
            if (src instanceof JAXMStreamSource) {
                try {
                    if (!SOAPPartImpl.lazyContentLength) {
                        ((JAXMStreamSource) src).reset();
                    }
                } catch (java.io.IOException ioe) {
                    log.severe("SAAJ0515.source.reset.exception");
                    throw new SOAPExceptionImpl(ioe);
                }
            }
        if (src instanceof LazyEnvelopeSource) {  
          return lazy((LazyEnvelopeSource)src, soapPart);
      }
        if (soapPart.message.isLazySoapBodyParsing()) {
            return parseEnvelopeStax(src, soapPart);
        } else {
            return parseEnvelopeSax(src, soapPart);
        }
    }
    
    private static Envelope lazy(LazyEnvelopeSource src, SOAPPartImpl soapPart) throws SOAPException {
            try {
        	StaxBridge staxBridge = new StaxLazySourceBridge(src, soapPart);
        	staxBridge.bridgeEnvelopeAndHeaders();            
            Envelope env = (Envelope) soapPart.getEnvelope();
            env.setStaxBridge(staxBridge);
            return env;
        } catch (XMLStreamException e) {
            throw new SOAPException(e);
        }
    }
    
    static private XMLInputFactory xmlInputFactory = null;
    
    private static Envelope parseEnvelopeStax(Source src, SOAPPartImpl soapPart)
            throws SOAPException {
        XMLStreamReader streamReader = null;
        if (src instanceof StAXSource) {
           streamReader = ((StAXSource) src).getXMLStreamReader(); 
        }
        try {
            if (streamReader == null) {
                if (xmlInputFactory == null) xmlInputFactory = XMLInputFactory.newInstance();
                streamReader = xmlInputFactory.createXMLStreamReader(src);
            }
//            SaajStaxWriter saajWriter = new SaajStaxWriter(soapPart.message, soapPart.document);
//            XMLStreamReaderToXMLStreamWriter readerWriterBridge = new XMLStreamReaderToXMLStreamWriter(
//                    streamReader, saajWriter, soapPart.getSOAPNamespace());
            
            StaxBridge readerWriterBridge = new StaxReaderBridge(streamReader, soapPart); 
            //bridge will stop reading at body element, and parse upon request, so save it
            //on the envelope
            readerWriterBridge.bridgeEnvelopeAndHeaders();
            
            Envelope env = (Envelope) soapPart.getEnvelope();
            env.setStaxBridge(readerWriterBridge);
            return env;
        } catch (Exception e) {
            throw new SOAPException(e);
        }
    }
    private static Envelope parseEnvelopeSax(Source src, SOAPPartImpl soapPart)
            throws SOAPException {
    	SAXParser saxParser = null;
	    ParserPool underlyingParserPool = parserPool.get();
	    try {
    		// Insert SAX filter to disallow Document Type Declarations since
    		// they are not legal in SOAP

    		if (src instanceof StreamSource) {
    			try {
    				saxParser = underlyingParserPool.get();
    			} catch (Exception e) {
    				log.severe("SAAJ0601.util.newSAXParser.exception");
    				throw new SOAPExceptionImpl(
    						"Couldn't get a SAX parser while constructing a envelope",
    						e);
    			}
    			InputSource is = SAXSource.sourceToInputSource(src);
    			if (is.getEncoding()== null && soapPart.getSourceCharsetEncoding() != null) {
    				is.setEncoding(soapPart.getSourceCharsetEncoding());
    			}
    			XMLReader rejectFilter;
    			try {
    				rejectFilter = new RejectDoctypeSaxFilter(saxParser);
    			} catch (Exception ex) {
    				log.severe("SAAJ0510.soap.cannot.create.envelope");
    				throw new SOAPExceptionImpl(
    						"Unable to create envelope from given source: ",
    						ex);
    			}
    			src = new SAXSource(rejectFilter, is);
    		}

    		try {
    			Transformer transformer =
    					EfficientStreamingTransformer.newTransformer();
    			DOMResult result = new DOMResult(soapPart);
    			transformer.transform(src, result);

    			Envelope env = (Envelope) soapPart.getEnvelope();
    			return env;
    		} catch (Exception ex) {
    			if (ex instanceof SOAPVersionMismatchException) {
    				throw (SOAPVersionMismatchException) ex;
    			}
    			log.severe("SAAJ0511.soap.cannot.create.envelope");
    			throw new SOAPExceptionImpl(
    					"Unable to create envelope from given source: ",
    					ex);
    		} 
    	} finally {
    		//no matter what condition occurs, always return the parser to the pool
            if (saxParser != null) {
                underlyingParserPool.returnParser(saxParser);
            }
        }
    }
}
