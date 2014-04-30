/*
 * Copyright (c) 2014 xipki.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 *
 */

package org.xipki.dbi;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;

import org.xipki.database.api.DataSource;
import org.xipki.dbi.ca.jaxb.CAConfigurationType;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.CaHasCertprofiles;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.CaHasPublishers;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.CaHasRequestors;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.Caaliases;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.Cas;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.Certprofiles;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.Crlsigners;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.Environments;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.Publishers;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.Requestors;
import org.xipki.dbi.ca.jaxb.CaHasCertprofileType;
import org.xipki.dbi.ca.jaxb.CaHasPublisherType;
import org.xipki.dbi.ca.jaxb.CaHasRequestorType;
import org.xipki.dbi.ca.jaxb.CaType;
import org.xipki.dbi.ca.jaxb.CaaliasType;
import org.xipki.dbi.ca.jaxb.CertprofileType;
import org.xipki.dbi.ca.jaxb.CmpcontrolType;
import org.xipki.dbi.ca.jaxb.CrlsignerType;
import org.xipki.dbi.ca.jaxb.EnvironmentType;
import org.xipki.dbi.ca.jaxb.ObjectFactory;
import org.xipki.dbi.ca.jaxb.PublisherType;
import org.xipki.dbi.ca.jaxb.RequestorType;
import org.xipki.dbi.ca.jaxb.ResponderType;
import org.xipki.security.api.PasswordResolverException;
import org.xipki.security.common.ParamChecker;

class CaConfigurationDbExporter extends DbPorter
{
    private final Marshaller marshaller;

    CaConfigurationDbExporter(DataSource dataSource, Marshaller marshaller, String destDir)
    throws SQLException, PasswordResolverException, IOException
    {
        super(dataSource, destDir);
        ParamChecker.assertNotNull("marshaller", marshaller);
        this.marshaller = marshaller;
    }

    public void export()
    throws Exception
    {
        CAConfigurationType caconf = new CAConfigurationType();
        caconf.setVersion(VERSION);

        System.out.println("Exporting CA configuration to database");

        CmpcontrolType cmpcontrol = export_cmpcontrol();
        if(cmpcontrol != null)
        {
            caconf.setCmpcontrol(cmpcontrol);
        }

        ResponderType responder = export_responder();
        if(responder != null)
        {
            caconf.setResponder(responder);
        }

        caconf.setEnvironments(export_environment());

        caconf.setCrlsigners(export_crlsigner());

        caconf.setRequestors(export_requestor());

        caconf.setPublishers(export_publisher());

        caconf.setCertprofiles(export_certprofile());

        caconf.setCas(export_ca());

        caconf.setCaaliases(export_caalias());

        caconf.setCaHasRequestors(export_ca_has_requestor());

        caconf.setCaHasPublishers(export_ca_has_publisher());

        caconf.setCaHasCertprofiles(export_ca_has_certprofile());

        JAXBElement<CAConfigurationType> root = new ObjectFactory().createCAConfiguration(caconf);
        marshaller.marshal(root, new File(baseDir + File.separator + FILENAME_CA_Configuration));

        System.out.println("Exporting CA configuration to database");
    }

    private CmpcontrolType export_cmpcontrol()
    throws SQLException
    {
        CmpcontrolType cmpcontrol = null;
        System.out.println("Exporting table CMPCONTROL");
        Statement stmt = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT REQUIRE_CONFIRM_CERT, SEND_CA_CERT, "
                    + " MESSAGE_TIME_BIAS, CONFIRM_WAIT_TIME"
                    + " FROM CMPCONTROL";
            ResultSet rs = stmt.executeQuery(sql);

            if(rs.next())
            {
                boolean requireConfirmCert = rs.getBoolean("REQUIRE_CONFIRM_CERT");
                boolean sendCaCert = rs.getBoolean("SEND_CA_CERT");
                int messageTimeBias = rs.getInt("MESSAGE_TIME_BIAS");
                int confirmWaitTime = rs.getInt("CONFIRM_WAIT_TIME");

                cmpcontrol = new CmpcontrolType();
                cmpcontrol.setRequireConfirmCert(requireConfirmCert);
                cmpcontrol.setSendCaCert(sendCaCert);
                cmpcontrol.setMessageTimeBias(messageTimeBias);
                cmpcontrol.setConfirmWaitTime(confirmWaitTime);
            }

            rs.close();
            rs = null;
        }finally
        {
            closeStatement(stmt);
        }

        System.out.println("Exported table CMPCONTROL");
        return cmpcontrol;
    }

    private Environments export_environment()
    throws SQLException
    {
        System.out.println("Exporting table ENVIRONMENT");
        Environments environments = new Environments();

        Statement stmt = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT NAME, VALUE FROM ENVIRONMENT";
            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String name = rs.getString("NAME");
                String value = rs.getString("VALUE");

                if("lock".equals(name))
                {
                    continue;
                }

                EnvironmentType environment = new EnvironmentType();
                environment.setName(name);
                environment.setValue(value);
                environments.getEnvironment().add(environment);
            }

            rs.close();
            rs = null;
        }finally
        {
            closeStatement(stmt);
        }

        System.out.println("Exported table ENVIRONMENT");
        return environments;
    }

    private Crlsigners export_crlsigner()
    throws SQLException
    {
        System.out.println("Exporting table CRLSIGNER");
        Crlsigners crlsigners = new Crlsigners();

        Statement stmt = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT NAME, SIGNER_TYPE, SIGNER_CONF, SIGNER_CERT, PERIOD,"
                    + " OVERLAP, INCLUDE_CERTS_IN_CRL"
                    + " FROM CRLSIGNER";
            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String name = rs.getString("NAME");
                String signer_type = rs.getString("SIGNER_TYPE");
                String signer_conf = rs.getString("SIGNER_CONF");
                String signer_cert = rs.getString("SIGNER_CERT");
                int period = rs.getInt("PERIOD");
                int overlap = rs.getInt("OVERLAP");
                boolean include_certs_in_crl = rs.getBoolean("INCLUDE_CERTS_IN_CRL");

                CrlsignerType crlsigner = new CrlsignerType();
                crlsigner.setName(name);
                crlsigner.setSignerType(signer_type);
                crlsigner.setSignerConf(signer_conf);
                crlsigner.setSignerCert(signer_cert);
                crlsigner.setPeriod(period);
                crlsigner.setOverlap(overlap);
                crlsigner.setIncludeCertsInCrl(include_certs_in_crl);

                crlsigners.getCrlsigner().add(crlsigner);
            }

            rs.close();
            rs = null;
        }finally
        {
            closeStatement(stmt);
        }

        System.out.println("Exported table CRLSIGNER");
        return crlsigners;
    }

    private Caaliases export_caalias()
    throws SQLException
    {
        System.out.println("Exporting table CAALIAS");
        Caaliases caaliases = new Caaliases();

        Statement stmt = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT NAME, CA_NAME FROM CAALIAS";
            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String name = rs.getString("NAME");
                String caName = rs.getString("CA_NAME");

                CaaliasType caalias = new CaaliasType();
                caalias.setName(name);
                caalias.setCaName(caName);

                caaliases.getCaalias().add(caalias);
            }

            rs.close();
            rs = null;
        }finally
        {
            closeStatement(stmt);
        }

        System.out.println("Exported table CAALIAS");
        return caaliases;
    }

    private Requestors export_requestor()
    throws SQLException
    {
        System.out.println("Exporting table REQUESTOR");
        Requestors requestors = new Requestors();

        Statement stmt = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT NAME, CERT FROM REQUESTOR";
            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String name = rs.getString("NAME");
                String cert = rs.getString("CERT");

                RequestorType requestor = new RequestorType();
                requestor.setName(name);
                requestor.setCert(cert);

                requestors.getRequestor().add(requestor);
            }

            rs.close();
            rs = null;
        }finally
        {
            closeStatement(stmt);
        }

        System.out.println("Exported table REQUESTOR");
        return requestors;
    }

    private ResponderType export_responder()
    throws SQLException
    {
        System.out.println("Exporting table RESPONDER");
        ResponderType responder = null;

        Statement stmt = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT TYPE, CERT, CONF FROM RESPONDER";
            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String type = rs.getString("TYPE");
                String conf = rs.getString("CONF");
                String cert = rs.getString("CERT");

                responder = new ResponderType();
                responder.setType(type);
                responder.setConf(conf);
                responder.setCert(cert);
            }

            rs.close();
            rs = null;
        }finally
        {
            closeStatement(stmt);
        }

        System.out.println("Exported table RESPONDER");
        return responder;
    }

    private Publishers export_publisher()
    throws SQLException
    {
        System.out.println("Exporting table PUBLISHER");
        Publishers publishers = new Publishers();

        Statement stmt = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT NAME, TYPE, CONF FROM PUBLISHER";
            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String name = rs.getString("NAME");
                String type = rs.getString("TYPE");
                String conf = rs.getString("CONF");

                PublisherType publisher = new PublisherType();
                publisher.setName(name);
                publisher.setType(type);
                publisher.setConf(conf);

                publishers.getPublisher().add(publisher);
            }

            rs.close();
            rs = null;
        }finally
        {
            closeStatement(stmt);
        }
        System.out.println("Exported table PUBLISHER");
        return publishers;
    }

    private Certprofiles export_certprofile()
    throws SQLException
    {
        System.out.println("Exporting table CERTPROFILE");
        Certprofiles certprofiles = new Certprofiles();

        Statement stmt = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT NAME, TYPE, CONF FROM CERTPROFILE";
            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String name = rs.getString("NAME");
                String type = rs.getString("TYPE");
                String conf = rs.getString("CONF");

                CertprofileType certprofile = new CertprofileType();
                certprofile.setName(name);
                certprofile.setType(type);
                certprofile.setConf(conf);

                certprofiles.getCertprofile().add(certprofile);
            }

            rs.close();
            rs = null;
        }finally
        {
            closeStatement(stmt);
        }

        System.out.println("Exported table CERTPROFILE");
        return certprofiles;
    }

    private Cas export_ca()
    throws SQLException
    {
        System.out.println("Exporting table CA");
        Cas cas = new Cas();

        Statement stmt = null;
        try
        {
            stmt = createStatement();

            String sqlPart1 = "SELECT NAME, NEXT_SERIAL, STATUS, CRL_URIS, OCSP_URIS, MAX_VALIDITY, "
                    + "CERT, SIGNER_TYPE, SIGNER_CONF, CRLSIGNER_NAME, "
                    + "ALLOW_DUPLICATE_KEY, ALLOW_DUPLICATE_SUBJECT, PERMISSIONS";
            String sqlPart2 = " FROM CA";

            ResultSet rs;
            boolean sqlWith_num_crls = true;
            try
            {
                String sql = sqlPart1 + ", NUM_CRLS" + sqlPart2;
                rs = stmt.executeQuery(sql);
            }catch(SQLException e)
            {
                sqlWith_num_crls = false;
                String sql = sqlPart1 + sqlPart2;
                rs = stmt.executeQuery(sql);
            }

            while(rs.next())
            {
                String name = rs.getString("NAME");
                String next_serial = rs.getString("NEXT_SERIAL");
                String status = rs.getString("STATUS");
                String crl_uris = rs.getString("CRL_URIS");
                String ocsp_uris = rs.getString("OCSP_URIS");
                int max_validity = rs.getInt("MAX_VALIDITY");
                String cert = rs.getString("CERT");
                String signer_type = rs.getString("SIGNER_TYPE");
                String signer_conf = rs.getString("SIGNER_CONF");
                String crlsigner_name = rs.getString("CRLSIGNER_NAME");
                boolean allowDuplicateKey = rs.getBoolean("ALLOW_DUPLICATE_KEY");
                boolean allowDuplicateSubject = rs.getBoolean("ALLOW_DUPLICATE_SUBJECT");
                Integer numCrls = null;
                if(sqlWith_num_crls)
                {
                    numCrls = rs.getInt("num_crls");
                }

                String permissions = rs.getString("PERMISSIONS");

                CaType ca = new CaType();
                ca.setName(name);
                ca.setNextSerial(next_serial);
                ca.setStatus(status);
                ca.setCrlUris(crl_uris);
                ca.setOcspUris(ocsp_uris);
                ca.setMaxValidity(max_validity);
                ca.setCert(cert);
                ca.setSignerType(signer_type);
                ca.setSignerConf(signer_conf);
                ca.setCrlsignerName(crlsigner_name);
                ca.setAllowDuplicateKey(allowDuplicateKey);
                ca.setAllowDuplicateSubject(allowDuplicateSubject);
                ca.setPermissions(permissions);
                ca.setNumCrls(numCrls);

                cas.getCa().add(ca);
            }

            rs.close();
            rs = null;
        }finally
        {
            closeStatement(stmt);
        }

        System.out.println("Exporting table CA");
        return cas;
    }

    private CaHasRequestors export_ca_has_requestor()
    throws SQLException
    {
        System.out.println("Exporting table CA_HAS_REQUESTOR");
        CaHasRequestors ca_has_requestors = new CaHasRequestors();

        Statement stmt = null;
        try
        {
            stmt = createStatement();

            String sql = "SELECT CA_NAME, REQUESTOR_NAME, RA, PERMISSIONS, PROFILES FROM CA_HAS_REQUESTOR";
            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String ca_name = rs.getString("CA_NAME");
                String requestor_name = rs.getString("REQUESTOR_NAME");
                boolean ra = rs.getBoolean("RA");
                String permissions = rs.getString("PERMISSIONS");
                String profiles = rs.getString("PROFILES");

                CaHasRequestorType ca_has_requestor = new CaHasRequestorType();
                ca_has_requestor.setCaName(ca_name);
                ca_has_requestor.setRequestorName(requestor_name);
                ca_has_requestor.setRa(ra);
                ca_has_requestor.setPermissions(permissions);
                ca_has_requestor.setProfiles(profiles);

                ca_has_requestors.getCaHasRequestor().add(ca_has_requestor);
            }

            rs.close();
            rs = null;
        }finally
        {
            closeStatement(stmt);
        }

        System.out.println("Exported table CA_HAS_REQUESTOR");
        return ca_has_requestors;
    }

    private CaHasPublishers export_ca_has_publisher()
    throws SQLException
    {
        System.out.println("Exporting table CA_HAS_PUBLISHER");
        CaHasPublishers ca_has_publishers = new CaHasPublishers();

        Statement stmt = null;
        try
        {
            stmt = createStatement();

            String sql = "SELECT CA_NAME, PUBLISHER_NAME FROM CA_HAS_PUBLISHER";
            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String ca_name = rs.getString("CA_NAME");
                String publisher_name = rs.getString("PUBLISHER_NAME");

                CaHasPublisherType ca_has_publisher = new CaHasPublisherType();
                ca_has_publisher.setCaName(ca_name);
                ca_has_publisher.setPublisherName(publisher_name);;

                ca_has_publishers.getCaHasPublisher().add(ca_has_publisher);
            }

            rs.close();
            rs = null;
        }finally
        {
            closeStatement(stmt);
        }

        System.out.println("Exported table CA_HAS_PUBLISHER");
        return ca_has_publishers;
    }

    private CaHasCertprofiles export_ca_has_certprofile()
    throws SQLException
    {
        System.out.println("Exporting table CA_HAS_CERTPROFILE");
        CaHasCertprofiles ca_has_certprofiles = new CaHasCertprofiles();

        Statement stmt = null;
        try
        {
            stmt = createStatement();

            String sql = "SELECT CA_NAME, CERTPROFILE_NAME FROM CA_HAS_CERTPROFILE";
            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String ca_name = rs.getString("CA_NAME");
                String certprofile_name = rs.getString("CERTPROFILE_NAME");

                CaHasCertprofileType ca_has_certprofile = new CaHasCertprofileType();
                ca_has_certprofile.setCaName(ca_name);
                ca_has_certprofile.setCertprofileName(certprofile_name);

                ca_has_certprofiles.getCaHasCertprofile().add(ca_has_certprofile);
            }

            rs.close();
            rs = null;
        }finally
        {
            closeStatement(stmt);
        }

        System.out.println("Exported table CA_HAS_CERTPROFILE");
        return ca_has_certprofiles;
    }

}