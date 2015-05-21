package fr.lteconsulting.pomexplorer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.alg.CycleDetector;
import org.jgrapht.alg.StrongConnectivityInspector;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DirectedMultigraph;

import com.mxgraph.layout.mxFastOrganicLayout;

import fr.lteconsulting.hexa.client.tools.Func1;
import fr.lteconsulting.hexa.client.tools.Func2;
import fr.lteconsulting.pomexplorer.WebServer.XWebServer;
import fr.lteconsulting.pomexplorer.web.commands.Command;
import fr.lteconsulting.pomexplorer.web.commands.CommandList;

/**
 * Hello world!
 */
public class App
{
	public static void main(String[] args)
	{
		App app = new App();
		app.run();
	}

	private void run()
	{
		XWebServer xWebServer = new XWebServer()
		{
			@Override
			public void onNewClient(Client client)
			{
				System.out.println("New client " + client.getId());
			}

			@Override
			public void onClientLeft(Client client)
			{
				System.out.println("Bye bye client !");
			}
		};

		Func1<String, String> service = new Func1<String, String>()
		{
			@Override
			public String exec(String query)
			{
				return "Super, you just asked for " + query;
			}
		};

		Func2<Client, String, String> socket = new Func2<Client, String, String>()
		{
			@Override
			public String exec(Client client, String query)
			{
				if (query == null || query.isEmpty())
					return "NOP.";

				String[] parts = query.split(" ");
				String command = parts[0];
				String[] parameters = new String[parts.length - 1];
				for (int i = 1; i < parts.length; i++)
					parameters[i - 1] = parts[i];

				Command cmd = CommandList.getCommand(command);
				if (cmd == null)
					return "Unknown command '" + command + "'";

				String result = cmd.execute(client, parameters);
				return result;
			}
		};

		WebServer server = new WebServer(xWebServer, service, socket);
		server.start();
	}

	private void test()
	{
		DirectedGraph<GAV, Dep> g = new DirectedMultigraph<GAV, Dep>(Dep.class);

		processFile(new File("C:\\tmp\\hexa.tools"), g);
		// processFile(new File("C:\\gr"), g);

		// GAV v;
		// TopologicalOrderIterator<GAV, Dep> orderIterator;
		//
		// orderIterator = new TopologicalOrderIterator<>(g);
		// System.out.println("\nOrdering:");
		// while (orderIterator.hasNext())
		// {
		// v = orderIterator.next();
		// System.out.println(v);
		// }
		//
		// System.out.println(g.toString());

		System.out.println("There are " + gavs.size() + " gavs");

		StrongConnectivityInspector<GAV, Dep> conn = new StrongConnectivityInspector<>(g);
		System.out.println("There are " + conn.stronglyConnectedSets().size() + " strongly connected components");

		ConnectivityInspector<GAV, Dep> ccon = new ConnectivityInspector<>(g);
		System.out.println("There are " + ccon.connectedSets().size() + " weakly connected components");
		for (Set<GAV> comp : ccon.connectedSets())
		{
			System.out.println("  - " + comp.toString());
		}

		CycleDetector<GAV, Dep> cycles = new CycleDetector<GAV, Dep>(g);
		System.out.println("Is there cycles ? " + cycles.detectCycles());

		JGraphXAdapter<GAV, Dep> ga = new JGraphXAdapter<>(g);
		GraphFrame frame = new GraphFrame(ga);

		mxFastOrganicLayout layout = new mxFastOrganicLayout(ga);
		layout.setUseBoundingBox(true);
		layout.setForceConstant(200);
		// mxCircleLayout layout = new mxCircleLayout(ga);
		layout.execute(ga.getDefaultParent());
	}

	private void processFile(File file, DirectedGraph<GAV, Dep> g)
	{
		if (file == null)
			return;

		if (file.isDirectory())
		{
			String name = file.getName();
			if ("target".equalsIgnoreCase(name) || "src".equalsIgnoreCase(name))
				return;

			for (File f : file.listFiles())
				processFile(f, g);
		}
		else if (file.getName().equalsIgnoreCase("pom.xml"))
		{
			processPom(file, g);
		}
	}

	private void processPom(File pom, DirectedGraph<GAV, Dep> g)
	{
		System.out.println("\n# Analysing pom file " + pom.getAbsolutePath());

		System.out.println("## Non-resolving analysis");
		MavenProject project = loadProject(pom);
		System.out.println(project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion() + ":"
				+ project.getPackaging());

		GAV gav = ensureArtifact(project.getGroupId(), project.getArtifactId(), project.getVersion(), g);

		Parent parent = project.getModel().getParent();
		if (parent != null)
		{
			System.out.println("   PARENT : " + parent.getId() + ":" + parent.getRelativePath());
		}

		Properties ptties = project.getProperties();
		if (ptties != null)
		{
			for (Entry<Object, Object> e : ptties.entrySet())
			{
				System.out.println("   PPTY: " + e.getKey() + " = " + e.getValue());
			}
		}
		
		if (project.getDependencyManagement() != null)
		{
			for (Dependency dependency : project.getDependencyManagement().getDependencies())
			{
				System.out.println("   MNGT: " + dependency.getGroupId() + ":" + dependency.getArtifactId() + ":"
						+ dependency.getVersion() + ":" + dependency.getClassifier() + ":" + dependency.getScope());

				GAV depGav = ensureArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), g);

				if (gav != null && depGav != null)
					g.addEdge(gav, depGav, new Dep(dependency.getScope(), dependency.getClassifier()));
			}
		}

		for (Dependency dependency : project.getDependencies())
		{
			System.out.println("   " + dependency.getGroupId() + ":" + dependency.getArtifactId() + ":"
					+ dependency.getVersion() + ":" + dependency.getClassifier() + ":" + dependency.getScope());

			GAV depGav = ensureArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), g);

			if (gav != null && depGav != null)
				g.addEdge(gav, depGav, new Dep(dependency.getScope(), dependency.getClassifier()));
		}

		System.out.println("## Resolving analysis");
		Toto.toto(pom);
	}

	HashMap<String, GAV> gavs = new HashMap<>();

	private GAV ensureArtifact(String groupId, String artifactId, String version, DirectedGraph<GAV, Dep> g)
	{
		// if (!groupId.startsWith("fr."))
		// return null;

		String sig = groupId + ":" + artifactId + ":" + version;
		GAV gav = gavs.get(sig);

		if (gav == null)
		{
			gav = new GAV(groupId, artifactId, version);
			gavs.put(sig, gav);
			g.addVertex(gav);
		}

		return gav;
	}

	private MavenProject loadProject(File pom)
	{
		Model model = null;
		FileReader reader = null;
		MavenXpp3Reader mavenreader = new MavenXpp3Reader();
		try
		{
			reader = new FileReader(pom);
		}
		catch (FileNotFoundException e1)
		{
		}
		try
		{
			model = mavenreader.read(reader);
			model.setPomFile(pom);
		}
		catch (IOException | XmlPullParserException e1)
		{
		}
		MavenProject project = new MavenProject(model);
	
		return project;
	}
}