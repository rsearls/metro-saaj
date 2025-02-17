/*
 * Copyright (c) 1997, 2021 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.messaging.saaj.soap.impl;

import java.util.logging.Level;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import com.sun.xml.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.messaging.saaj.soap.SOAPDocument;
import com.sun.xml.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.messaging.saaj.soap.name.NameImpl;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import jakarta.xml.soap.Name;
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPHeader;
import jakarta.xml.soap.SOAPHeaderElement;

public abstract class HeaderImpl extends ElementImpl implements SOAPHeader {
    protected static final boolean MUST_UNDERSTAND_ONLY = false;

    protected HeaderImpl(SOAPDocumentImpl ownerDoc, NameImpl name) {
        super(ownerDoc, name);
    }

    public HeaderImpl(SOAPDocumentImpl ownerDoc, Element domElement) {
        super(ownerDoc, domElement);
    }

    protected abstract SOAPHeaderElement createHeaderElement(Name name)
        throws SOAPException;
    protected abstract SOAPHeaderElement createHeaderElement(QName name)
        throws SOAPException;
    protected abstract NameImpl getNotUnderstoodName();
    protected abstract NameImpl getUpgradeName();
    protected abstract NameImpl getSupportedEnvelopeName();

    @Override
    public SOAPHeaderElement addHeaderElement(Name name) throws SOAPException {
        SOAPElement newHeaderElement =
            ElementFactory.createNamedElement(
                ((SOAPDocument) getOwnerDocument()).getDocument(),
                name.getLocalName(),
                name.getPrefix(),
                name.getURI());
        if (newHeaderElement == null
            || !(newHeaderElement instanceof SOAPHeaderElement)) {
            newHeaderElement = createHeaderElement(name);
        }

        // header elements must be namespace qualified
        // check that URI is  not empty, ensuring that the element is NS qualified.
        String uri = newHeaderElement.getElementQName().getNamespaceURI();
        if ((uri == null) || ("").equals(uri)) {
            log.severe("SAAJ0131.impl.header.elems.ns.qualified");
            throw new SOAPExceptionImpl("HeaderElements must be namespace qualified");
        }
        addNode(newHeaderElement);
        return (SOAPHeaderElement) newHeaderElement;
    }

    @Override
    public SOAPHeaderElement addHeaderElement(QName name) throws SOAPException {
        SOAPElement newHeaderElement =
            ElementFactory.createNamedElement(
                ((SOAPDocument) getOwnerDocument()).getDocument(),
                name.getLocalPart(),
                name.getPrefix(),
                name.getNamespaceURI());
        if (newHeaderElement == null
            || !(newHeaderElement instanceof SOAPHeaderElement)) {
            newHeaderElement = createHeaderElement(name);
        }

        // header elements must be namespace qualified
        // check that URI is  not empty, ensuring that the element is NS qualified.
        String uri = newHeaderElement.getElementQName().getNamespaceURI();
        if ((uri == null) || ("").equals(uri)) {
            log.severe("SAAJ0131.impl.header.elems.ns.qualified");
            throw new SOAPExceptionImpl("HeaderElements must be namespace qualified");
        }
        addNode(newHeaderElement);
        return (SOAPHeaderElement) newHeaderElement;
    }

    @Override
    protected SOAPElement addElement(Name name) throws SOAPException {
        return addHeaderElement(name);
    }

    @Override
    protected SOAPElement addElement(QName name) throws SOAPException {
        return addHeaderElement(name);
    }

    @Override
    public Iterator<SOAPHeaderElement> examineHeaderElements(String actor) {
        return getHeaderElementsForActor(actor, false, false);
    }

    @Override
    public Iterator<SOAPHeaderElement> extractHeaderElements(String actor) {
        return getHeaderElementsForActor(actor, true, false);
    }

    protected Iterator<SOAPHeaderElement> getHeaderElementsForActor(
        String actor,
        boolean detach,
        boolean mustUnderstand) {
        if (actor == null || actor.equals("")) {
            log.severe("SAAJ0132.impl.invalid.value.for.actor.or.role");
            throw new IllegalArgumentException("Invalid value for actor or role");
        }
        return getHeaderElements(actor, detach, mustUnderstand);
    }

    protected Iterator<SOAPHeaderElement> getHeaderElements(
        String actor,
        boolean detach,
        boolean mustUnderstand) {
        List<SOAPHeaderElement> elementList = new ArrayList<>();

        Iterator<jakarta.xml.soap.Node> eachChild = getChildElements();

        org.w3c.dom.Node currentChild = iterate(eachChild);
        while (currentChild != null) {
            if (!(currentChild instanceof SOAPHeaderElement)) {
                currentChild = iterate(eachChild);
            } else {
                HeaderElementImpl currentElement =
                    (HeaderElementImpl) currentChild;
                currentChild = iterate(eachChild);

                boolean isMustUnderstandMatching =
                    (!mustUnderstand || currentElement.getMustUnderstand());
                boolean doAdd = false;
                if (actor == null && isMustUnderstandMatching) {
                    doAdd = true;
                } else {
                    String currentActor = currentElement.getActorOrRole();
                    if (currentActor == null) {
                        currentActor = "";
                    }

                    if (currentActor.equalsIgnoreCase(actor)
                        && isMustUnderstandMatching) {
                        doAdd = true;
                    }
                }

                if (doAdd) {
                    elementList.add(currentElement);
                    if (detach) {
                        currentElement.detachNode();
                    }
                }
            }
        }

        return elementList.listIterator();
    }

    private <T> T iterate(Iterator<T> each) {
        return each.hasNext() ? each.next() : null;
    }

    @Override
    public void setParentElement(SOAPElement element) throws SOAPException {
        if (!(element instanceof SOAPEnvelope)) {
            log.severe("SAAJ0133.impl.header.parent.mustbe.envelope");
            throw new SOAPException("Parent of SOAPHeader has to be a SOAPEnvelope");
        }
        super.setParentElement(element);
    }

    // overriding ElementImpl's method to ensure that HeaderElements are
    // namespace qualified. Holds for both SOAP versions.
    // TODO - This check needs to be made for other addChildElement() methods
    // as well.
    @Override
    public SOAPElement addChildElement(String localName) throws SOAPException {

        SOAPElement element = super.addChildElement(localName);
        // check that URI is  not empty, ensuring that the element is NS qualified.
        String uri = element.getElementName().getURI();
        if ((uri == null) || ("").equals(uri)) {
            log.severe("SAAJ0134.impl.header.elems.ns.qualified");
            throw new SOAPExceptionImpl("HeaderElements must be namespace qualified");
        }
        return element;
    }

    @Override
    public Iterator<SOAPHeaderElement> examineAllHeaderElements() {
        return getHeaderElements(null, false, MUST_UNDERSTAND_ONLY);
    }

    @Override
    public Iterator<SOAPHeaderElement> examineMustUnderstandHeaderElements(String actor) {
        return getHeaderElements(actor, false, true);

    }

    @Override
    public Iterator<SOAPHeaderElement> extractAllHeaderElements() {
        return getHeaderElements(null, true, false);
    }

    @Override
    public SOAPHeaderElement addUpgradeHeaderElement(Iterator supportedSoapUris)
        throws SOAPException {
        if (supportedSoapUris == null) {
            log.severe("SAAJ0411.ver1_2.no.null.supportedURIs");
            throw new SOAPException("Argument cannot be null; iterator of supportedURIs cannot be null");
        }
        if (!supportedSoapUris.hasNext()) {
            log.severe("SAAJ0412.ver1_2.no.empty.list.of.supportedURIs");
            throw new SOAPException("List of supported URIs cannot be empty");
        }
        Name upgradeName = getUpgradeName();
        SOAPHeaderElement upgradeHeaderElement =
            (SOAPHeaderElement) addChildElement(upgradeName);
        Name supportedEnvelopeName = getSupportedEnvelopeName();
        int i = 0;
        while (supportedSoapUris.hasNext()) {
            SOAPElement subElement =
                upgradeHeaderElement.addChildElement(supportedEnvelopeName);
            String ns = "ns" + Integer.toString(i);
            subElement.addAttribute(
                NameImpl.createFromUnqualifiedName("qname"),
                ns + ":Envelope");
            subElement.addNamespaceDeclaration(
                ns, (String) supportedSoapUris.next());
            i ++;
        }
        return upgradeHeaderElement;
    }

    @Override
    public SOAPHeaderElement addUpgradeHeaderElement(String supportedSoapUri)
        throws SOAPException {
        return addUpgradeHeaderElement(new String[] {supportedSoapUri});
    }

    @Override
    public SOAPHeaderElement addUpgradeHeaderElement(String[] supportedSoapUris)
        throws SOAPException {

        if (supportedSoapUris == null) {
            log.severe("SAAJ0411.ver1_2.no.null.supportedURIs");
            throw new SOAPException("Argument cannot be null; array of supportedURIs cannot be null");
        }
        if (supportedSoapUris.length == 0) {
            log.severe("SAAJ0412.ver1_2.no.empty.list.of.supportedURIs");
            throw new SOAPException("List of supported URIs cannot be empty");
        }
        Name upgradeName = getUpgradeName();
        SOAPHeaderElement upgradeHeaderElement =
            (SOAPHeaderElement) addChildElement(upgradeName);
        Name supportedEnvelopeName = getSupportedEnvelopeName();
        for (int i = 0; i < supportedSoapUris.length; i ++) {
            SOAPElement subElement =
                upgradeHeaderElement.addChildElement(supportedEnvelopeName);
            String ns = "ns" + Integer.toString(i);
            subElement.addAttribute(
                NameImpl.createFromUnqualifiedName("qname"),
                ns + ":Envelope");
            subElement.addNamespaceDeclaration(ns, supportedSoapUris[i]);
        }
        return upgradeHeaderElement;
    }

    @Override
    protected SOAPElement convertToSoapElement(Element element) {
        final org.w3c.dom.Node soapNode = getSoapDocument().findIfPresent(element);
        if (soapNode instanceof SOAPHeaderElement) {
            return (SOAPElement) soapNode;
        } else {
            SOAPHeaderElement headerElement;
            try {
                headerElement =
                    createHeaderElement(NameImpl.copyElementName(element));
            } catch (SOAPException e) {
                throw new ClassCastException("Could not convert Element to SOAPHeaderElement: " + e.getMessage());
            }
            return replaceElementWithSOAPElement(
                element,
                (ElementImpl) headerElement);
        }
    }

    @Override
    public SOAPElement setElementQName(QName newName) throws SOAPException {
       log.log(Level.SEVERE,
                "SAAJ0146.impl.invalid.name.change.requested",
                new Object[] {elementQName.getLocalPart(),
                              newName.getLocalPart()});
        throw new SOAPException("Cannot change name for "
                                + elementQName.getLocalPart() + " to "
                                + newName.getLocalPart());
    }

}
