package coursier.bootstrap.launcher;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import coursier.bootstrap.launcher.jar.JarFile;
import coursier.paths.Mirror;
import coursier.paths.Mirror.MirrorPropertiesException;

class ClassLoaders {

    final static String resourceDir = "coursier/bootstrap/launcher/";
    protected final String prefix;
    final String defaultURLResource;
    private final List<Mirror> mirrors = Mirror.load();
    protected final Download download;

    ClassLoaders(Download download, String prefix) throws MirrorPropertiesException, IOException {
        this.prefix = prefix;
        this.defaultURLResource = resourceDir + prefix + "-jar-urls";
        this.download = download;
    }

    List<URL> getURLs(String[] rawURLs) {

        List<String> errors = new ArrayList<>();
        List<URL> urls = new ArrayList<>();

        for (String urlStr : rawURLs) {
            String urlStr0 = Mirror.transform(mirrors, urlStr);
            try {
                URL url = URI.create(urlStr0).toURL();
                urls.add(url);
            } catch (Exception ex) {
                String message = urlStr0 + ": " + ex.getMessage();
                errors.add(message);
            }
        }

        if (!errors.isEmpty()) {
            StringBuilder builder = new StringBuilder("Error:");
            for (String error: errors) {
                builder.append("\n  ");
                builder.append(error);
            }
            System.err.println(builder.toString());
            System.exit(1);
        }

        return urls;
    }

    ClassLoader readBaseLoaders(ClassLoader baseLoader) throws IOException {

        ClassLoader parentLoader = baseLoader;
        int i = 1;
        while (true) {
            String[] strUrls = Util.readStringSequence(resourceDir + "bootstrap-jar-urls-" + i, baseLoader);
            String nameOrNull = Util.readString(resourceDir + "bootstrap-loader-name-" + i, baseLoader);

            if (strUrls.length == 0)
                break;

            List<URL> urls = getURLs(strUrls);
            List<URL> localURLs = download.getLocalURLs(urls);

            if (nameOrNull == null)
                parentLoader = new URLClassLoader(localURLs.toArray(new URL[0]), parentLoader);
            else
                parentLoader = new SharedClassLoader(localURLs.toArray(new URL[0]), parentLoader, new String[] {nameOrNull});

            i = i + 1;
        }

        return parentLoader;
    }

    ClassLoader createClassLoader(ClassLoader contextLoader) throws IOException {

        String[] strUrls = Util.readStringSequence(defaultURLResource, contextLoader);
        List<URL> urls = getURLs(strUrls);
        List<URL> localURLs = download.getLocalURLs(urls);

        ClassLoader hideStuffClassLoader = new HideNativeApiClassLoader(contextLoader);
        ClassLoader parentClassLoader = readBaseLoaders(hideStuffClassLoader);

        return new URLClassLoader(localURLs.toArray(new URL[0]), parentClassLoader);
    }

    Download getDownload() {
        return download;
    }

    JarFile sourceJarFileOrNull() {
        return null;
    }

}
