package fr.perigee.commonsvfs.webdav;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.provider.AbstractFileName;
import org.apache.commons.vfs2.provider.AbstractFileObject;
import org.apache.commons.vfs2.provider.AbstractFileSystem;
import org.apache.commons.vfs2.provider.UriParser;
import org.apache.commons.vfs2.util.FileObjectUtils;
import org.apache.commons.vfs2.util.MonitorOutputStream;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;

public class WebdavFileObject extends AbstractFileObject implements FileObject {
	/**
	 * Output stream used to write data to remote file
	 * @author ndx
	 *
	 */
	public class WebdavOutputStream extends MonitorOutputStream {

		private WebdavFileObject written;


		public WebdavOutputStream() {
			super(new ByteArrayOutputStream());
		}

		/**
		 * On close, the file is written
		 * @throws IOException
		 * @see org.apache.commons.vfs2.util.MonitorOutputStream#onClose()
		 */
		@Override
		protected void onClose() throws IOException {
			sardine.put(getUrl(), ((ByteArrayOutputStream) out).toByteArray());
		}
	}

	private static final String MIME_DIRECTORY = "httpd/unix-directory";
	private Sardine sardine;
	private WebdavFileSystemConfigBuilder builder;
	private String urlCharset;

	public WebdavFileObject(AbstractFileName name, AbstractFileSystem fileSystem, Sardine sardine) {
		super(name, fileSystem);
		this.sardine = sardine;
        builder = (WebdavFileSystemConfigBuilder) WebdavFileSystemConfigBuilder.getInstance();
        this.urlCharset = builder.getUrlCharset(getFileSystem().getFileSystemOptions());
	}
	
	@Override
	public URLFileName getName() {
		return (URLFileName) super.getName();
	}
	
	

	@Override
	protected FileType doGetType() throws Exception {
		if(sardine.exists(getUrl())) {
			DavResource serverSide = getServerSide();
			if(serverSide.getContentType().equals(MIME_DIRECTORY))
				return FileType.FOLDER;
			else
				return FileType.FILE;
		} else {
			return FileType.IMAGINARY;
		}
	}

	private DavResource getServerSide() throws IOException {
		return getServerSide(getUrl(), getName().getPath());
	}

	/**
	 * Get server-side resource associated to url
	 * @param url
	 * @return
	 * @throws IOException 
	 */
	private DavResource getServerSide(String url, String path) throws IOException {
		List<DavResource> resources = sardine.list(url);
		for(DavResource res : resources) {
			if(res.getPath().equals(path))
				return res;
			else if(!path.endsWith("/") && res.getPath().equals(path+"/"))
				return res;
		}
		return null;
	}

	@Override
	protected String[] doListChildren() throws Exception {
		// Shouldn't be called for simple files
		if(getType()==FileType.FOLDER) {
			List<DavResource> resources = sardine.list(getUrl());
			List<String> children = new LinkedList<String>();
			String url=getUrl();
			for(DavResource res : resources) {
				
				String childUrl=res.getHref().toString();
				StringBuilder childUrlSb=new StringBuilder(childUrl);
				UriParser.normalisePath(childUrlSb);
				String childUrlNormalized=childUrlSb.toString();
				if(url.equals(childUrlNormalized)) { // compare normalized URLs
					children.add(childUrl); // but use the non-normalized URL because the trailing slash is used to determine the type (file/folder)
				}
			}
			return children.toArray(new String[children.size()]);
		} else {
			return null;
		}
	}

	@Override
	protected long doGetContentSize() throws Exception {
		return getServerSide().getContentLength();
	}

	@Override
	protected InputStream doGetInputStream() throws Exception {
		return sardine.get(getUrl());
	}
	
	@Override
	protected void doCreateFolder() throws Exception {
		sardine.createDirectory(getUrl());
	}
	
	@Override
	protected OutputStream doGetOutputStream(boolean bAppend) throws Exception {
        return new WebdavOutputStream();
	}
	
	@Override
	protected void doDelete() throws Exception {
		sardine.delete(getUrl());
	}
	
	@Override
	protected long doGetLastModifiedTime() throws Exception {
		return getServerSide().getModified().getTime();
	}
	
	@Override
	protected boolean doIsSameFile(FileObject destFile) throws FileSystemException {
		return destFile.getURL().equals(getURL());
	}
	
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected Map<String, Object> doGetAttributes() throws Exception {
		List<DavResource> resources=sardine.list(getUrl(), 0);
		if (resources.isEmpty()) throw new FileSystemException("vfs.provider/get-attributes.error", this);
		if (resources.size()>1) throw new FileSystemException("vfs.provider/get-attributes.error", this);
		
		DavResource resource=resources.get(0);

		return (Map)resource.getCustomProps(); // cast is valid (Map<String,String> is always compatible with Map<String,Object>)
	}
	
	@Override
	protected void doSetAttribute(String attrName, Object value) throws Exception {
		Map<String, String> properties = new HashMap<String, String>(1);
		properties.put(attrName, value.toString());
		sardine.setCustomProps(getUrl(), properties, null);
	}
	
	@Override
	protected void doRemoveAttribute(String attrName) throws Exception {
		List<String> properties = new LinkedList<String>();
		properties.add(attrName);
		sardine.setCustomProps(getUrl(), null, properties);
	}

	public String getUrl() {
		// should this be cached?
		return urlString(getName());
	}


    private String urlString(URLFileName name)
    {
        return urlString(name, true, urlCharset);
    }

    /**
     * Convert the FileName to an encoded url String.
     *
     * @param name The FileName.
     * @param includeUserInfo true if user information should be included.
     * @param urlCharset expected charset of urls (may be null)
     * @return The encoded URL String.
     */
    static String urlString(URLFileName name, boolean includeUserInfo, String urlCharset)
    {
        String user = null;
        String password = null;
        if (includeUserInfo)
        {
            user = name.getUserName();
            password = name.getPassword();
        }
        
        String scheme;
        if ("webdavs".equalsIgnoreCase(name.getScheme())) scheme="https";
        else scheme="http";
        
        URLFileName newFile = new URLFileName(scheme, name.getHostName(), name.getPort(),
                name.getDefaultPort(), user, password,
                name.getPath(), name.getType(), name.getQueryString());
        try
        {
            return newFile.getURIEncoded(urlCharset);
        }
        catch (Exception e)
        {
            return name.getURI();
        }
    }
    
}
