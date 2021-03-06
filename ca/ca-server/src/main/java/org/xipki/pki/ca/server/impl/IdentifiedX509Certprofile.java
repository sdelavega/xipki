/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2013 - 2016 Lijun Liao
 * Author: Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.pki.ca.server.impl;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x500.DirectoryString;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.xipki.commons.common.util.CollectionUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.ExtensionExistence;
import org.xipki.commons.security.KeyUsage;
import org.xipki.commons.security.ObjectIdentifiers;
import org.xipki.commons.security.util.X509Util;
import org.xipki.pki.ca.api.BadCertTemplateException;
import org.xipki.pki.ca.api.BadFormatException;
import org.xipki.pki.ca.api.EnvParameterResolver;
import org.xipki.pki.ca.api.profile.CertValidity;
import org.xipki.pki.ca.api.profile.CertprofileException;
import org.xipki.pki.ca.api.profile.ExtensionControl;
import org.xipki.pki.ca.api.profile.ExtensionValue;
import org.xipki.pki.ca.api.profile.ExtensionValues;
import org.xipki.pki.ca.api.profile.GeneralNameMode;
import org.xipki.pki.ca.api.profile.x509.AuthorityInfoAccessControl;
import org.xipki.pki.ca.api.profile.x509.ExtKeyUsageControl;
import org.xipki.pki.ca.api.profile.x509.KeyUsageControl;
import org.xipki.pki.ca.api.profile.x509.SpecialX509CertprofileBehavior;
import org.xipki.pki.ca.api.profile.x509.SubjectDnSpec;
import org.xipki.pki.ca.api.profile.x509.SubjectInfo;
import org.xipki.pki.ca.api.profile.x509.X509CertLevel;
import org.xipki.pki.ca.api.profile.x509.X509CertVersion;
import org.xipki.pki.ca.api.profile.x509.X509Certprofile;
import org.xipki.pki.ca.server.impl.util.CaUtil;
import org.xipki.pki.ca.server.mgmt.api.CertprofileEntry;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

class IdentifiedX509Certprofile {

    private static final Set<ASN1ObjectIdentifier> CRITICAL_ONLY_EXTENSION_TYPES;

    private static final Set<ASN1ObjectIdentifier> CA_CRITICAL_ONLY_EXTENSION_TYPES;

    private static final Set<ASN1ObjectIdentifier> NONCRITICAL_ONLY_EXTENSION_TYPES;

    private static final Set<ASN1ObjectIdentifier> CA_ONLY_EXTENSION_TYPES;

    private static final Set<ASN1ObjectIdentifier> NONE_REQUEST_EXTENSION_TYPES;

    private static final Set<ASN1ObjectIdentifier> REQUIRED_CA_EXTENSION_TYPES;

    private static final Set<ASN1ObjectIdentifier> REQUIRED_EE_EXTENSION_TYPES;

    static {
        CRITICAL_ONLY_EXTENSION_TYPES = new HashSet<>();
        CRITICAL_ONLY_EXTENSION_TYPES.add(Extension.keyUsage);
        CRITICAL_ONLY_EXTENSION_TYPES.add(Extension.policyMappings);
        CRITICAL_ONLY_EXTENSION_TYPES.add(Extension.nameConstraints);
        CRITICAL_ONLY_EXTENSION_TYPES.add(Extension.policyConstraints);
        CRITICAL_ONLY_EXTENSION_TYPES.add(Extension.inhibitAnyPolicy);
        CRITICAL_ONLY_EXTENSION_TYPES.add(ObjectIdentifiers.id_pe_tlsfeature);

        CA_CRITICAL_ONLY_EXTENSION_TYPES = new HashSet<>();
        CA_CRITICAL_ONLY_EXTENSION_TYPES.add(Extension.basicConstraints);

        NONCRITICAL_ONLY_EXTENSION_TYPES = new HashSet<>();
        NONCRITICAL_ONLY_EXTENSION_TYPES.add(Extension.authorityKeyIdentifier);
        NONCRITICAL_ONLY_EXTENSION_TYPES.add(Extension.subjectKeyIdentifier);
        NONCRITICAL_ONLY_EXTENSION_TYPES.add(Extension.issuerAlternativeName);
        NONCRITICAL_ONLY_EXTENSION_TYPES.add(Extension.subjectDirectoryAttributes);
        NONCRITICAL_ONLY_EXTENSION_TYPES.add(Extension.freshestCRL);
        NONCRITICAL_ONLY_EXTENSION_TYPES.add(Extension.authorityInfoAccess);
        NONCRITICAL_ONLY_EXTENSION_TYPES.add(Extension.subjectInfoAccess);

        CA_ONLY_EXTENSION_TYPES = new HashSet<>();
        CA_ONLY_EXTENSION_TYPES.add(Extension.policyMappings);
        CA_ONLY_EXTENSION_TYPES.add(Extension.nameConstraints);
        CA_ONLY_EXTENSION_TYPES.add(Extension.policyConstraints);
        CA_ONLY_EXTENSION_TYPES.add(Extension.inhibitAnyPolicy);

        NONE_REQUEST_EXTENSION_TYPES = new HashSet<ASN1ObjectIdentifier>();
        NONE_REQUEST_EXTENSION_TYPES.add(Extension.subjectKeyIdentifier);
        NONE_REQUEST_EXTENSION_TYPES.add(Extension.authorityKeyIdentifier);
        NONE_REQUEST_EXTENSION_TYPES.add(Extension.issuerAlternativeName);
        NONE_REQUEST_EXTENSION_TYPES.add(Extension.cRLDistributionPoints);
        NONE_REQUEST_EXTENSION_TYPES.add(Extension.freshestCRL);
        NONE_REQUEST_EXTENSION_TYPES.add(Extension.basicConstraints);
        NONE_REQUEST_EXTENSION_TYPES.add(Extension.inhibitAnyPolicy);

        REQUIRED_CA_EXTENSION_TYPES = new HashSet<>();
        REQUIRED_CA_EXTENSION_TYPES.add(Extension.basicConstraints);
        REQUIRED_CA_EXTENSION_TYPES.add(Extension.subjectKeyIdentifier);
        REQUIRED_CA_EXTENSION_TYPES.add(Extension.keyUsage);

        REQUIRED_EE_EXTENSION_TYPES = new HashSet<>();
        REQUIRED_EE_EXTENSION_TYPES.add(Extension.authorityKeyIdentifier);
        REQUIRED_EE_EXTENSION_TYPES.add(Extension.basicConstraints);
        REQUIRED_EE_EXTENSION_TYPES.add(Extension.subjectKeyIdentifier);
    } // end static

    private final String name;
    private final CertprofileEntry dbEntry;
    private final X509Certprofile certprofile;

    IdentifiedX509Certprofile(final CertprofileEntry dbEntry, final X509Certprofile certProfile)
    throws CertprofileException {
        this.dbEntry = ParamUtil.requireNonNull("entry", dbEntry);
        this.name = dbEntry.getName();
        this.certprofile = ParamUtil.requireNonNull("certProfile", certProfile);

        this.certprofile.initialize(dbEntry.getConf());
        if (certProfile.getSpecialCertprofileBehavior()
                == SpecialX509CertprofileBehavior.gematik_gSMC_K) {
            String paramName = SpecialX509CertprofileBehavior.PARAMETER_MAXLIFTIME;
            String str = certProfile.getParameter(paramName);
            if (str == null) {
                throw new CertprofileException("parameter " + paramName + " is not defined");
            }

            str = str.trim();
            int idx;
            try {
                idx = Integer.parseInt(str);
            } catch (NumberFormatException ex) {
                throw new CertprofileException("invalid " + paramName + ": " + str);
            }
            if (idx < 1) {
                throw new CertprofileException("invalid " + paramName + ": " + str);
            }
        }

    } // constructor

    public String getName() {
        return name;
    }

    public CertprofileEntry getDbEntry() {
        return dbEntry;
    }

    public X509CertVersion getVersion() {
        return certprofile.getVersion();
    }

    public List<String> getSignatureAlgorithms() {
        return certprofile.getSignatureAlgorithms();
    }

    public SpecialX509CertprofileBehavior getSpecialCertprofileBehavior() {
        return certprofile.getSpecialCertprofileBehavior();
    }

    public void setEnvParameterResolver(final EnvParameterResolver envParameterResolver) {
        if (certprofile != null) {
            certprofile.setEnvParameterResolver(envParameterResolver);
        }
    }

    public Date getNotBefore(final Date notBefore) {
        return certprofile.getNotBefore(notBefore);
    }

    public CertValidity getValidity() {
        return certprofile.getValidity();
    }

    public boolean hasMidnightNotBefore() {
        return certprofile.hasMidnightNotBefore();
    }

    public TimeZone getTimezone() {
        return certprofile.getTimezone();
    }

    public SubjectInfo getSubject(final X500Name requestedSubject)
    throws CertprofileException, BadCertTemplateException {
        SubjectInfo subjectInfo = certprofile.getSubject(requestedSubject);
        RDN[] countryRdns = subjectInfo.getGrantedSubject().getRDNs(ObjectIdentifiers.DN_C);
        if (countryRdns != null) {
            for (RDN rdn : countryRdns) {
                String textValue = IETFUtils.valueToString(rdn.getFirst().getValue());
                if (!SubjectDnSpec.isValidCountryAreaCode(textValue)) {
                    throw new BadCertTemplateException("invalid country/area code '" + textValue
                            + "'");
                }
            }
        }
        return subjectInfo;
    }

    public ExtensionValues getExtensions(@Nonnull final X500Name requestedSubject,
            @Nullable final Extensions requestExtensions,
            @Nonnull final SubjectPublicKeyInfo publicKeyInfo,
            @Nonnull final PublicCaInfo publicCaInfo, @Nullable final X509Certificate crlSignerCert,
            @Nonnull final Date notBefore, @Nonnull final Date notAfter)
    throws CertprofileException, BadCertTemplateException {
        ParamUtil.requireNonNull("publicKeyInfo", publicKeyInfo);
        ExtensionValues values = new ExtensionValues();

        Map<ASN1ObjectIdentifier, ExtensionControl> controls
                = new HashMap<>(certprofile.getExtensionControls());

        Set<ASN1ObjectIdentifier> neededExtensionTypes = new HashSet<>();
        Set<ASN1ObjectIdentifier> wantedExtensionTypes = new HashSet<>();
        if (requestExtensions != null) {
            Extension reqExtension = requestExtensions.getExtension(
                    ObjectIdentifiers.id_xipki_ext_cmpRequestExtensions);
            if (reqExtension != null) {
                ExtensionExistence ee = ExtensionExistence.getInstance(
                        reqExtension.getParsedValue());
                neededExtensionTypes.addAll(ee.getNeedExtensions());
                wantedExtensionTypes.addAll(ee.getWantExtensions());
            }

            for (ASN1ObjectIdentifier oid : neededExtensionTypes) {
                if (wantedExtensionTypes.contains(oid)) {
                    wantedExtensionTypes.remove(oid);
                }

                if (!controls.containsKey(oid)) {
                    throw new BadCertTemplateException(
                            "could not add needed extension " + oid.getId());
                }
            }
        }

        // SubjectKeyIdentifier
        ASN1ObjectIdentifier extType = Extension.subjectKeyIdentifier;
        ExtensionControl extControl = controls.remove(extType);
        if (extControl != null
                && addMe(extType, extControl, neededExtensionTypes, wantedExtensionTypes)) {
            MessageDigest sha1;
            try {
                sha1 = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException ex) {
                throw new CertprofileException(ex.getMessage(), ex);
            }
            sha1.reset();

            byte[] encodedSpki = publicKeyInfo.getPublicKeyData().getBytes();
            byte[] skiValue = sha1.digest(encodedSpki);
            SubjectKeyIdentifier value = new SubjectKeyIdentifier(skiValue);
            addExtension(values, extType, value, extControl,
                    neededExtensionTypes, wantedExtensionTypes);
        }

        // Authority key identifier
        extType = Extension.authorityKeyIdentifier;
        extControl = controls.remove(extType);
        if (extControl != null
                && addMe(extType, extControl, neededExtensionTypes, wantedExtensionTypes)) {
            byte[] ikiValue = publicCaInfo.getSubjectKeyIdentifer();
            AuthorityKeyIdentifier value = null;
            if (ikiValue != null) {
                if (certprofile.includeIssuerAndSerialInAki()) {
                    GeneralNames x509CaSubject = new GeneralNames(
                            new GeneralName(publicCaInfo.getX500Subject()));
                    value = new AuthorityKeyIdentifier(ikiValue, x509CaSubject,
                            publicCaInfo.getSerialNumber());
                } else {
                    value = new AuthorityKeyIdentifier(ikiValue);
                }
            }

            addExtension(values, extType, value, extControl, neededExtensionTypes,
                    wantedExtensionTypes);
        }

        // IssuerAltName
        extType = Extension.issuerAlternativeName;
        extControl = controls.remove(extType);
        if (extControl != null
                && addMe(extType, extControl, neededExtensionTypes, wantedExtensionTypes)) {
            GeneralNames value = publicCaInfo.getSubjectAltName();
            addExtension(values, extType, value, extControl, neededExtensionTypes,
                    wantedExtensionTypes);
        }

        // AuthorityInfoAccess
        extType = Extension.authorityInfoAccess;
        extControl = controls.remove(extType);
        if (extControl != null
                && addMe(extType, extControl, neededExtensionTypes, wantedExtensionTypes)) {
            AuthorityInfoAccessControl aiaControl = certprofile.getAiaControl();

            List<String> caIssuers = null;
            if (aiaControl == null || aiaControl.includesCaIssuers()) {
                caIssuers = publicCaInfo.getCaCertUris();
            }

            List<String> ocspUris = null;
            if (aiaControl == null || aiaControl.includesOcsp()) {
                ocspUris = publicCaInfo.getOcspUris();
            }

            if (CollectionUtil.isNonEmpty(caIssuers) || CollectionUtil.isNonEmpty(ocspUris)) {
                AuthorityInformationAccess value = CaUtil.createAuthorityInformationAccess(
                        caIssuers, ocspUris);
                addExtension(values, extType, value, extControl, neededExtensionTypes,
                        wantedExtensionTypes);
            }
        }

        if (controls.containsKey(Extension.cRLDistributionPoints)
                || controls.containsKey(Extension.freshestCRL)) {
            X500Name crlSignerSubject = null;
            if (crlSignerCert != null) {
                crlSignerSubject = X500Name.getInstance(
                        crlSignerCert.getSubjectX500Principal().getEncoded());
            }

            X500Name x500CaPrincipal = publicCaInfo.getX500Subject();

            // CRLDistributionPoints
            extType = Extension.cRLDistributionPoints;
            extControl = controls.remove(extType);
            if (extControl != null
                    && addMe(extType, extControl, neededExtensionTypes, wantedExtensionTypes)) {
                try {
                    if (CollectionUtil.isNonEmpty(publicCaInfo.getCrlUris())) {
                        CRLDistPoint value = CaUtil.createCrlDistributionPoints(
                                publicCaInfo.getCrlUris(), x500CaPrincipal, crlSignerSubject);
                        addExtension(values, extType, value, extControl,
                                neededExtensionTypes, wantedExtensionTypes);
                    }
                } catch (IOException ex) {
                    throw new CertprofileException(ex.getMessage(), ex);
                }
            }

            // FreshestCRL
            extType = Extension.freshestCRL;
            extControl = controls.remove(extType);
            if (extControl != null
                    && addMe(extType, extControl, neededExtensionTypes, wantedExtensionTypes)) {
                try {
                    if (CollectionUtil.isNonEmpty(publicCaInfo.getDeltaCrlUris())) {
                        CRLDistPoint value = CaUtil.createCrlDistributionPoints(
                            publicCaInfo.getDeltaCrlUris(),x500CaPrincipal, crlSignerSubject);
                        addExtension(values, extType, value, extControl, neededExtensionTypes,
                                wantedExtensionTypes);
                    }
                } catch (IOException ex) {
                    throw new CertprofileException(ex.getMessage(), ex);
                }
            }
        }

        // BasicConstraints
        extType = Extension.basicConstraints;
        extControl = controls.remove(extType);
        if (extControl != null
                && addMe(extType, extControl, neededExtensionTypes, wantedExtensionTypes)) {
            BasicConstraints value = CaUtil.createBasicConstraints(certprofile.getCertLevel(),
                    certprofile.getPathLenBasicConstraint());
            addExtension(values, extType, value, extControl, neededExtensionTypes,
                    wantedExtensionTypes);
        }

        // KeyUsage
        extType = Extension.keyUsage;
        extControl = controls.remove(extType);
        if (extControl != null
                && addMe(extType, extControl, neededExtensionTypes, wantedExtensionTypes)) {
            Set<KeyUsage> usages = new HashSet<>();
            Set<KeyUsageControl> usageOccs = certprofile.getKeyUsage();
            for (KeyUsageControl k : usageOccs) {
                if (k.isRequired()) {
                    usages.add(k.getKeyUsage());
                }
            }

            // the optional KeyUsage will only be set if requested explicitly
            if (requestExtensions != null && extControl.isRequest()) {
                addRequestedKeyusage(usages, requestExtensions, usageOccs);
            }

            org.bouncycastle.asn1.x509.KeyUsage value = X509Util.createKeyUsage(usages);
            addExtension(values, extType, value, extControl, neededExtensionTypes,
                    wantedExtensionTypes);
        }

        // ExtendedKeyUsage
        extType = Extension.extendedKeyUsage;
        extControl = controls.remove(extType);
        if (extControl != null
                && addMe(extType, extControl, neededExtensionTypes, wantedExtensionTypes)) {
            List<ASN1ObjectIdentifier> usages = new LinkedList<>();
            Set<ExtKeyUsageControl> usageOccs = certprofile.getExtendedKeyUsages();
            for (ExtKeyUsageControl k : usageOccs) {
                if (k.isRequired()) {
                    usages.add(k.getExtKeyUsage());
                }
            }

            // the optional ExtKeyUsage will only be set if requested explicitly
            if (requestExtensions != null && extControl.isRequest()) {
                addRequestedExtKeyusage(usages, requestExtensions, usageOccs);
            }

            if (extControl.isCritical()
                    && usages.contains(ObjectIdentifiers.id_anyExtendedKeyUsage)) {
                extControl = new ExtensionControl(false, extControl.isRequired(),
                        extControl.isRequest());
            }

            ExtendedKeyUsage value = X509Util.createExtendedUsage(usages);
            addExtension(values, extType, value, extControl, neededExtensionTypes,
                    wantedExtensionTypes);
        }

        // ocsp-nocheck
        extType = ObjectIdentifiers.id_extension_pkix_ocsp_nocheck;
        extControl = controls.remove(extType);
        if (extControl != null
                && addMe(extType, extControl, neededExtensionTypes, wantedExtensionTypes)) {
            // the extension ocsp-nocheck will only be set if requested explicitly
            DERNull value = DERNull.INSTANCE;
            addExtension(values, extType, value, extControl, neededExtensionTypes,
                    wantedExtensionTypes);
        }

        // SubjectAltName
        extType = Extension.subjectAlternativeName;
        extControl = controls.remove(extType);
        if (extControl != null && addMe(extType, extControl, neededExtensionTypes,
                wantedExtensionTypes)) {
            GeneralNames value = null;
            if (requestExtensions != null && extControl.isRequest()) {
                value = createRequestedSubjectAltNames(requestExtensions,
                        certprofile.getSubjectAltNameModes());
            }
            addExtension(values, extType, value, extControl, neededExtensionTypes,
                    wantedExtensionTypes);
        }

        // SubjectInfoAccess
        extType = Extension.subjectInfoAccess;
        extControl = controls.remove(extType);
        if (extControl != null && addMe(extType, extControl, neededExtensionTypes,
                wantedExtensionTypes)) {
            ASN1Sequence value = null;
            if (requestExtensions != null && extControl.isRequest()) {
                value = createSubjectInfoAccess(requestExtensions,
                        certprofile.getSubjectInfoAccessModes());
            }
            addExtension(values, extType, value, extControl, neededExtensionTypes,
                    wantedExtensionTypes);
        }

        ExtensionValues subvalues = certprofile.getExtensions(
                Collections.unmodifiableMap(controls), requestedSubject, requestExtensions,
                notBefore, notAfter);

        Set<ASN1ObjectIdentifier> extTypes = new HashSet<>(controls.keySet());
        for (ASN1ObjectIdentifier type : extTypes) {
            extControl = controls.remove(type);
            boolean addMe = addMe(type, extControl, neededExtensionTypes, wantedExtensionTypes);
            if (addMe) {
                ExtensionValue value = null;
                if (extControl.isRequest()) {
                    Extension reqExt = requestExtensions.getExtension(type);
                    if (reqExt != null) {
                        value = new ExtensionValue(reqExt.isCritical(), reqExt.getParsedValue());
                    }
                }

                if (value == null) {
                    value = subvalues.getExtensionValue(type);
                }

                addExtension(values, type, value, extControl, neededExtensionTypes,
                        wantedExtensionTypes);
            }
        }

        Set<ASN1ObjectIdentifier> unprocessedExtTypes = new HashSet<>();
        for (ASN1ObjectIdentifier type : controls.keySet()) {
            if (controls.get(type).isRequired()) {
                unprocessedExtTypes.add(type);
            }
        }

        if (CollectionUtil.isNonEmpty(unprocessedExtTypes)) {
            throw new CertprofileException(
                    "could not add required extensions " + toString(unprocessedExtTypes));
        }

        if (CollectionUtil.isNonEmpty(neededExtensionTypes)) {
            throw new BadCertTemplateException(
                    "could not add requested extensions " + toString(neededExtensionTypes));
        }

        return values;
    } // method getExtensions

    public X509CertLevel getCertLevel() {
        return certprofile.getCertLevel();
    }

    public boolean isOnlyForRa() {
        return certprofile.isOnlyForRa();
    }

    public SubjectPublicKeyInfo checkPublicKey(final SubjectPublicKeyInfo publicKey)
    throws BadCertTemplateException {
        ParamUtil.requireNonNull("publicKey", publicKey);
        return certprofile.checkPublicKey(publicKey);
    }

    public boolean incSerialNumberIfSubjectExists() {
        return certprofile.incSerialNumberIfSubjectExists();
    }

    public void shutdown() {
        if (certprofile != null) {
            certprofile.shutdown();
        }
    }

    public boolean includeIssuerAndSerialInAki() {
        return certprofile.includeIssuerAndSerialInAki();
    }

    public String incSerialNumber(final String currentSerialNumber) throws BadFormatException {
        return certprofile.incSerialNumber(currentSerialNumber);
    }

    public boolean isDuplicateKeyPermitted() {
        return certprofile.isDuplicateKeyPermitted();
    }

    public boolean isDuplicateSubjectPermitted() {
        return certprofile.isDuplicateSubjectPermitted();
    }

    public boolean isSerialNumberInReqPermitted() {
        return certprofile.isSerialNumberInReqPermitted();
    }

    public String getParameter(final String paramName) {
        return certprofile.getParameter(paramName);
    }

    public Map<ASN1ObjectIdentifier, ExtensionControl> getExtensionControls() {
        return certprofile.getExtensionControls();
    }

    public Set<KeyUsageControl> getKeyUsage() {
        return certprofile.getKeyUsage();
    }

    public Integer getPathLenBasicConstraint() {
        return certprofile.getPathLenBasicConstraint();
    }

    public Set<ExtKeyUsageControl> getExtendedKeyUsages() {
        return certprofile.getExtendedKeyUsages();
    }

    public int getMaxCertSize() {
        return certprofile.getMaxCertSize();
    }

    public void validate() throws CertprofileException {
        StringBuilder msg = new StringBuilder();

        Map<ASN1ObjectIdentifier, ExtensionControl> controls = getExtensionControls();

        // make sure that non-request extensions are not permitted in requests
        Set<ASN1ObjectIdentifier> set = new HashSet<>();
        for (ASN1ObjectIdentifier type : NONE_REQUEST_EXTENSION_TYPES) {
            ExtensionControl control = controls.get(type);
            if (control != null && control.isRequest()) {
                set.add(type);
            }
        }

        if (CollectionUtil.isNonEmpty(set)) {
            msg.append("extensions ").append(toString(set))
                .append(" must not be contained in request, ");
        }

        X509CertLevel level = getCertLevel();
        boolean ca = (level == X509CertLevel.RootCA) || (level == X509CertLevel.SubCA);

        // make sure that CA-only extensions are not permitted in EE certificate
        set.clear();
        if (!ca) {
            set.clear();
            for (ASN1ObjectIdentifier type : CA_ONLY_EXTENSION_TYPES) {
                if (controls.containsKey(type)) {
                    set.add(type);
                }
            }

            if (CollectionUtil.isNonEmpty(set)) {
                msg.append("EE profile contains CA-only extensions ").append(toString(set))
                    .append(", ");
            }
        }

        // make sure that critical only extensions are not marked as non-critical.
        set.clear();
        for (ASN1ObjectIdentifier type : controls.keySet()) {
            ExtensionControl control = controls.get(type);
            if (CRITICAL_ONLY_EXTENSION_TYPES.contains(type)) {
                if (!control.isCritical()) {
                    set.add(type);
                }
            }

            if (ca && CA_CRITICAL_ONLY_EXTENSION_TYPES.contains(type)) {
                if (!control.isCritical()) {
                    set.add(type);
                }
            }
        }

        if (CollectionUtil.isNonEmpty(set)) {
            msg.append("critical only extensions are marked as non-critical ")
                .append(toString(set)).append(", ");
        }

        // make sure that non-critical only extensions are not marked as critical.
        set.clear();
        for (ASN1ObjectIdentifier type : controls.keySet()) {
            ExtensionControl control = controls.get(type);
            if (NONCRITICAL_ONLY_EXTENSION_TYPES.contains(type)) {
                if (control.isCritical()) {
                    set.add(type);
                }
            }
        }

        if (CollectionUtil.isNonEmpty(set)) {
            msg.append("non-critical extensions are marked as critical ").append(toString(set))
                .append(", ");
        }

        // make sure that required extensions are present
        set.clear();
        Set<ASN1ObjectIdentifier> requiredTypes = ca ? REQUIRED_CA_EXTENSION_TYPES
                : REQUIRED_EE_EXTENSION_TYPES;

        for (ASN1ObjectIdentifier type : requiredTypes) {
            ExtensionControl extCtrl = controls.get(type);
            if (extCtrl == null || !extCtrl.isRequired()) {
                set.add(type);
            }
        }

        if (level == X509CertLevel.SubCA) {
            ASN1ObjectIdentifier type = Extension.authorityKeyIdentifier;
            ExtensionControl extCtrl = controls.get(type);
            if (extCtrl == null || !extCtrl.isRequired()) {
                set.add(type);
            }
        }

        if (!set.isEmpty()) {
            msg.append("required extensions are not marked as required ")
                .append(toString(set)).append(", ");
        }

        // KeyUsage
        Set<KeyUsageControl> usages = getKeyUsage();

        if (ca) {
            // make sure the CA certificate contains usage keyCertSign
            if (!containsKeyusage(usages, KeyUsage.keyCertSign)) {
                msg.append("CA profile does not contain keyUsage ")
                    .append(KeyUsage.keyCertSign).append(", ");
            }
        } else {
            // make sure the EE certificate does not contain CA-only usages
            KeyUsage[] caOnlyUsages = new KeyUsage[] {KeyUsage.keyCertSign, KeyUsage.cRLSign};

            Set<KeyUsage> setUsages = new HashSet<>();
            for (KeyUsage caOnlyUsage : caOnlyUsages) {
                if (containsKeyusage(usages, caOnlyUsage)) {
                    setUsages.add(caOnlyUsage);
                }
            }

            if (CollectionUtil.isNonEmpty(set)) {
                msg.append("EE profile contains CA-only keyUsage ").append(setUsages).append(", ");
            }
        }

        int len = msg.length();
        if (len > 2) {
            msg.delete(len - 2, len);
            throw new CertprofileException(msg.toString());
        }
    } // method validate

    private static String toString(final Set<ASN1ObjectIdentifier> oids) {
        if (oids == null) {
            return "null";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (ASN1ObjectIdentifier oid : oids) {
            String name = ObjectIdentifiers.getName(oid);
            if (name != null) {
                sb.append(name);
                sb.append(" (").append(oid.getId()).append(")");
            } else {
                sb.append(oid.getId());
            }
            sb.append(", ");
        }
        if (CollectionUtil.isNonEmpty(oids)) {
            int len = sb.length();
            sb.delete(len - 2, len);
        }
        sb.append("]");

        return sb.toString();
    } // method toString

    private static boolean containsKeyusage(final Set<KeyUsageControl> usageControls,
            final KeyUsage usage) {
        for (KeyUsageControl entry : usageControls) {
            if (usage == entry.getKeyUsage()) {
                return true;
            }
        }
        return false;
    }

    private static GeneralName createGeneralName(final GeneralName reqName,
            final Set<GeneralNameMode> modes) throws BadCertTemplateException {
        int tag = reqName.getTagNo();
        GeneralNameMode mode = null;
        for (GeneralNameMode m : modes) {
            if (m.getTag().getTag() == tag) {
                mode = m;
                break;
            }
        }

        if (mode == null) {
            throw new BadCertTemplateException("generalName tag " + tag + " is not allowed");
        }

        switch (tag) {
        case GeneralName.rfc822Name:
        case GeneralName.dNSName:
        case GeneralName.uniformResourceIdentifier:
        case GeneralName.iPAddress:
        case GeneralName.registeredID:
        case GeneralName.directoryName:
            return new GeneralName(tag, reqName.getName());
        case GeneralName.otherName:
            ASN1Sequence reqSeq = ASN1Sequence.getInstance(reqName.getName());
            int size = reqSeq.size();
            if (size != 2) {
                throw new BadCertTemplateException("invalid otherName sequence: size is not 2: "
                        + size);
            }

            ASN1ObjectIdentifier type = ASN1ObjectIdentifier.getInstance(reqSeq.getObjectAt(0));
            if (!mode.getAllowedTypes().contains(type)) {
                throw new BadCertTemplateException(
                        "otherName.type " + type.getId() + " is not allowed");
            }

            ASN1Encodable asn1 = reqSeq.getObjectAt(1);
            if (! (asn1 instanceof ASN1TaggedObject)) {
                throw new BadCertTemplateException("otherName.value is not tagged Object");
            }

            int tagNo = ((ASN1TaggedObject) asn1).getTagNo();
            if (tagNo != 0) {
                throw new BadCertTemplateException("otherName.value does not have tag 0: " + tagNo);
            }

            ASN1EncodableVector vector = new ASN1EncodableVector();
            vector.add(type);
            vector.add(new DERTaggedObject(true, 0, ((ASN1TaggedObject) asn1).getObject()));
            DERSequence seq = new DERSequence(vector);

            return new GeneralName(GeneralName.otherName, seq);
        case GeneralName.ediPartyName:
            reqSeq = ASN1Sequence.getInstance(reqName.getName());

            size = reqSeq.size();
            String nameAssigner = null;
            int idx = 0;
            if (size > 1) {
                DirectoryString ds = DirectoryString.getInstance(
                        ((ASN1TaggedObject) reqSeq.getObjectAt(idx++)).getObject());
                nameAssigner = ds.getString();
            }

            DirectoryString ds = DirectoryString.getInstance(
                    ((ASN1TaggedObject) reqSeq.getObjectAt(idx++)).getObject());
            String partyName = ds.getString();

            vector = new ASN1EncodableVector();
            if (nameAssigner != null) {
                vector.add(new DERTaggedObject(false, 0, new DirectoryString(nameAssigner)));
            }
            vector.add(new DERTaggedObject(false, 1, new DirectoryString(partyName)));
            seq = new DERSequence(vector);
            return new GeneralName(GeneralName.ediPartyName, seq);
        default:
            throw new RuntimeException("should not reach here, unknown GeneralName tag " + tag);
        } // end switch (tag)
    } // method createGeneralName

    private static boolean addMe(final ASN1ObjectIdentifier extType,
            final ExtensionControl extControl, final Set<ASN1ObjectIdentifier> neededExtensionTypes,
            final Set<ASN1ObjectIdentifier> wantedExtensionTypes) {
        boolean addMe = extControl.isRequired();
        if (!addMe) {
            if (neededExtensionTypes.contains(extType) || wantedExtensionTypes.contains(extType)) {
                addMe = true;
            }
        }
        return addMe;
    } // method addMe

    private static void addRequestedKeyusage(final Set<KeyUsage> usages,
            final Extensions requestExtensions, final Set<KeyUsageControl> usageOccs) {
        Extension extension = requestExtensions.getExtension(Extension.keyUsage);
        if (extension == null) {
            return;
        }

        org.bouncycastle.asn1.x509.KeyUsage reqKeyUsage =
                org.bouncycastle.asn1.x509.KeyUsage.getInstance(extension.getParsedValue());
        for (KeyUsageControl k : usageOccs) {
            if (k.isRequired()) {
                continue;
            }

            if (reqKeyUsage.hasUsages(k.getKeyUsage().getBcUsage())) {
                usages.add(k.getKeyUsage());
            }
        }
    } // method addRequestedKeyusage

    private static void addRequestedExtKeyusage(final List<ASN1ObjectIdentifier> usages,
            final Extensions requestExtensions, final Set<ExtKeyUsageControl> usageOccs) {
        Extension extension = requestExtensions.getExtension(Extension.extendedKeyUsage);
        if (extension == null) {
            return;
        }

        ExtendedKeyUsage reqKeyUsage =
                ExtendedKeyUsage.getInstance(extension.getParsedValue());
        for (ExtKeyUsageControl k : usageOccs) {
            if (k.isRequired()) {
                continue;
            }

            if (reqKeyUsage.hasKeyPurposeId(KeyPurposeId.getInstance(k.getExtKeyUsage()))) {
                usages.add(k.getExtKeyUsage());
            }
        }
    } // method addRequestedExtKeyusage

    private static GeneralNames createRequestedSubjectAltNames(final Extensions requestExtensions,
            final Set<GeneralNameMode> modes) throws BadCertTemplateException {
        ASN1Encodable extValue = requestExtensions.getExtensionParsedValue(
                Extension.subjectAlternativeName);
        if (extValue == null) {
            return null;
        }

        GeneralNames reqNames = GeneralNames.getInstance(extValue);
        if (modes == null) {
            return reqNames;
        }

        GeneralName[] reqL = reqNames.getNames();
        GeneralName[] grantedNames = new GeneralName[reqL.length];
        for (int i = 0; i < reqL.length; i++) {
            grantedNames[i] = createGeneralName(reqL[i], modes);
        }
        return new GeneralNames(grantedNames);
    } // method createRequestedSubjectAltNames

    private static ASN1Sequence createSubjectInfoAccess(final Extensions requestExtensions,
            final Map<ASN1ObjectIdentifier, Set<GeneralNameMode>> modes)
    throws BadCertTemplateException {
        ASN1Encodable extValue = requestExtensions.getExtensionParsedValue(
                Extension.subjectInfoAccess);
        if (extValue == null) {
            return null;
        }

        ASN1Sequence reqSeq = ASN1Sequence.getInstance(extValue);
        int size = reqSeq.size();

        if (modes == null) {
            return reqSeq;
        }

        ASN1EncodableVector vec = new ASN1EncodableVector();
        for (int i = 0; i < size; i++) {
            AccessDescription ad = AccessDescription.getInstance(reqSeq.getObjectAt(i));
            ASN1ObjectIdentifier accessMethod = ad.getAccessMethod();
            Set<GeneralNameMode> generalNameModes = modes.get(accessMethod);

            if (generalNameModes == null) {
                throw new BadCertTemplateException("subjectInfoAccess.accessMethod "
                        + accessMethod.getId() + " is not allowed");
            }

            GeneralName accessLocation = createGeneralName(ad.getAccessLocation(),
                    generalNameModes);
            vec.add(new AccessDescription(accessMethod, accessLocation));
        } // end for

        return vec.size() > 0 ? new DERSequence(vec) : null;
    } // method createSubjectInfoAccess

    private static void addExtension(final ExtensionValues values,
            final ASN1ObjectIdentifier extType, final ExtensionValue extValue,
            final ExtensionControl extControl, final Set<ASN1ObjectIdentifier> neededExtensionTypes,
            final Set<ASN1ObjectIdentifier> wantedExtensionTypes) throws CertprofileException {
        if (extValue != null) {
            values.addExtension(extType, extValue);
            neededExtensionTypes.remove(extType);
            wantedExtensionTypes.remove(extType);
            return;
        }

        if (!extControl.isRequired()) {
            return;
        }

        String description = ObjectIdentifiers.getName(extType);
        if (description == null) {
            description = extType.getId();
        }
        throw new CertprofileException("could not add required extension " + description);
    } // method addExtension

    private static void addExtension(final ExtensionValues values,
            final ASN1ObjectIdentifier extType, final ASN1Encodable extValue,
            final ExtensionControl extControl, final Set<ASN1ObjectIdentifier> neededExtensionTypes,
            final Set<ASN1ObjectIdentifier> wantedExtensionTypes) throws CertprofileException {
        if (extValue != null) {
            values.addExtension(extType, extControl.isCritical(), extValue);
            neededExtensionTypes.remove(extType);
            wantedExtensionTypes.remove(extType);
            return;
        }

        if (!extControl.isRequired()) {
            return;
        }

        String description = ObjectIdentifiers.getName(extType);
        if (description == null) {
            description = extType.getId();
        }
        throw new CertprofileException("could not add required extension " + description);
    } // method addExtension

}
