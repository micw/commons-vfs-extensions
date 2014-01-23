Commons-VFS Webdav implementation using Webdav
=====

Title says quite all, no ? This project was started as an answer to both a question on [StackOverflow](http://stackoverflow.com/q/9920816/15619), and to [one on my blog](http://riduidel.wordpress.com/2013/02/12/commons-vfs/#comment-255) asking for some code.

Want to integrate that in your commons-vfs installation ?

Simple !

Insert (and create, if it is not yet the case) a META-INF/vfs-providers.xml file containing at least

	<provider
		class-name="fr.perigee.commonsvfs.webdav.WebdavFileProvider">
		<scheme name="webdav" />
		<scheme name="webdavs" />
	</provider>

And make sure CommonsVFS is loaded with this configuration file, and it will work.


Or configure it programmatically:

	StandardFileSystemManager fsManager = (StandardFileSystemManager) VFS.getManager();
	fsManager.addProvider("webdav", new fr.perigee.commonsvfs.webdav.WebdavFileProvider());
	fsManager.addProvider("webdavs", new fr.perigee.commonsvfs.webdav.WebdavFileProvider());

Use it:

	String url="webdavs://"+URLEncoder.encode(siteLogin)+"x:"+URLEncoder.encode(sitePasswd)+"@"+siteHost+resourcePath;
	FileObject fo=fsManager.resolveFile(url);
