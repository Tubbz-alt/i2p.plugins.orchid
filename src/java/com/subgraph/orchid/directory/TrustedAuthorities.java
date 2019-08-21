package com.subgraph.orchid.directory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.subgraph.orchid.DirectoryServer;
import com.subgraph.orchid.Tor;
import com.subgraph.orchid.data.HexDigest;
import com.subgraph.orchid.data.IPv4Address;
import com.subgraph.orchid.directory.parsing.DocumentFieldParser;
import com.subgraph.orchid.directory.parsing.DocumentParsingHandler;

/*
 * This class contains the hardcoded 'bootstrap' directory authority
 * server information. 
 * https://github.com/torproject/tor/blob/master/src/app/config/auth_dirs.inc
 */
public class TrustedAuthorities {
	
	private final static String[] dirServers = {
	    "authority moria1 orport=9101 v3ident=D586D18309DED4CD6D57C18FDB97EFA96D330566 128.31.0.39:9131 9695 DFC3 5FFE B861 329B 9F1A B04C 4639 7020 CE31",
	    "authority tor26 v1 orport=443 v3ident=14C131DFC5C6F93646BE72FA1401C02A8DF2E8B4 86.59.21.38:80 847B 1F85 0344 D787 6491 A548 92F9 0493 4E4E B85D",
	    "authority dizum orport=443 v3ident=E8A9C45EDE6D711294FADF8E7951F4DE6CA56B58 45.66.33.45:80 7EA6 EAD6 FD83 083C 538F 4403 8BBF A077 587D D755",
	    "authority longclaw orport=443 v3ident=23D15D965BC35114467363C165C4F724B64B4F66 199.58.81.140:80 74A9 1064 6BCE EFBC D2E8 74FC 1DC9 9743 0F96 8145",
	    "authority dannenberg orport=443 v3ident=0232AF901C31A04EE9848595AF9BB7620D4C5B2E 193.23.244.244:80 7BE6 83E6 5D48 1413 21C5 ED92 F075 C553 64AC 7123",
	    "authority maatuska orport=80 v3ident=49015F787433103580E3B66A1707A00E60F2D15B 171.25.193.9:443 BD6A 8292 55CB 08E6 6FBE 7D37 4836 3586 E46B 3810",
	    "authority Faravahar orport=443 v3ident=EFCBE720AB3A82B99F9E953CD5BF50F7EEFC7B97 154.35.175.225:80 CF6D 0AAF B385 BE71 B8E1 11FC 5CFF 4B47 9237 33BC",
	    "authority gabelmoo orport=443 v3ident=ED03BB616EB2F60BEC80151114BB25CEF515B226 131.188.40.189:80 F204 4413 DAC2 E02E 3D6B CF47 35A1 9BCA 1DE9 7281",
	    "authority bastet orport=443 v3ident=27102BC123E7AF1D4741AE047E160C91ADC76B21 204.13.164.118:80 24E2 F139 121D 4394 C54B 5BCC 368B 3B41 1857 C413",
	    // bridges don't work with orchid? and non-bridges listed after bridges don't work either
	    // "authority Bifroest orport=443 bridge 37.218.247.217:80 1D8F 3A91 C37C 5D1C 4C19 B1AD 1D0C FBE8 BF72 D8E1",
	};

	private final List<DirectoryServer> directoryServers = new ArrayList<DirectoryServer>();
	private final int v3ServerCount;
	
	private final static TrustedAuthorities _instance = new TrustedAuthorities();
	
	public static TrustedAuthorities getInstance() {
		return _instance;
	}
	
    private TrustedAuthorities() {
    	initialize();
    	v3ServerCount = countV3Servers();
    }
    
    private int countV3Servers() {
    	int n = 0;
    	for(DirectoryServer ds: directoryServers) {
    		if(ds.getV3Identity() != null) {
    			n += 1;
    		}
    	}
    	return n;
    }
    
	void initialize() {
		final StringBuilder builder = new StringBuilder();
		for(String entry: dirServers) {
			builder.append(entry);
			builder.append('\n');
		}
		final ByteBuffer buffer = ByteBuffer.wrap(builder.toString().getBytes(Tor.getDefaultCharset()));
		final DocumentFieldParser parser = new DocumentFieldParserImpl(buffer);
		
		parser.setHandler(new DocumentParsingHandler() {
			public void endOfDocument() {}
			public void parseKeywordLine() { processKeywordLine(parser);}
		});
		parser.processDocument();
	}
	
	private void processKeywordLine(DocumentFieldParser fieldParser) {
		final DirectoryAuthorityStatus status = new DirectoryAuthorityStatus();
		status.setNickname(fieldParser.parseNickname());
		while(fieldParser.argumentsRemaining() > 0) 
			processArgument(fieldParser, status);
	}
	
	private void processArgument(DocumentFieldParser fieldParser, DirectoryAuthorityStatus status) {
		final String item = fieldParser.parseString();
		if(Character.isDigit(item.charAt(0))) {
			parseAddressPort(fieldParser, item, status);
			status.setIdentity(fieldParser.parseFingerprint());
			DirectoryServerImpl server = new DirectoryServerImpl(status);
			if(status.getV3Ident() != null) {
				server.setV3Ident(status.getV3Ident());
			}
			fieldParser.logDebug("Adding trusted authority: " + server);
			directoryServers.add(server);
			return;
		} else {
			parseFlag(fieldParser, item, status);
		}
	}
	
	private void parseAddressPort(DocumentFieldParser parser, String item, DirectoryAuthorityStatus status) {
		final String[] args = item.split(":");
		status.setAddress(IPv4Address.createFromString(args[0]));
		status.setDirectoryPort(parser.parsePort(args[1]));	
	}
	
	private void parseFlag(DocumentFieldParser parser, String flag, DirectoryAuthorityStatus status) {
		if(flag.equals("v1")) {
			status.setV1Authority();
			status.setHiddenServiceAuthority();
		} else if(flag.equals("hs")) {
			status.setHiddenServiceAuthority();
		} else if(flag.equals("no-hs")) {
			status.unsetHiddenServiceAuthority();
		} else if(flag.equals("bridge")) {
			status.setBridgeAuthority();
		} else if(flag.equals("no-v2")) {
			status.unsetV2Authority();
		} else if(flag.startsWith("orport=")) {
			status.setRouterPort( parser.parsePort(flag.substring(7)));
		} else if(flag.startsWith("v3ident=")) {
			status.setV3Ident(HexDigest.createFromString(flag.substring(8)));
		}
	}
	
	public int getV3AuthorityServerCount() {
		return v3ServerCount;
	}

	public List<DirectoryServer> getAuthorityServers() {
		return directoryServers;
	}

	public DirectoryServer getAuthorityServerByIdentity(HexDigest identity) {
		for(DirectoryServer ds: directoryServers) {
			if(identity.equals(ds.getV3Identity())) {
				return ds;
			}
		}
		return null;
	}
}
