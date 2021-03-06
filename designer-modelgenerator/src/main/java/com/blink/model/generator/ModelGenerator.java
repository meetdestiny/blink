package com.blink.model.generator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.blink.designer.model.App;
import com.blink.designer.model.Entity;
import com.blink.designer.model.EntityAttribute;
import com.blink.designer.model.Type;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;

public class ModelGenerator {

	@PersistenceContext
	private EntityManager entityManager;

	private String localRepo;

	private static final String targetClassesLocation = "../../../target/classes";
	private static final String targetPackageLocation = "../../../target/bin";
	private static final String JAVA_SUFFIX= ".java";

	public File generateModel(App app, String srcLocation) throws IOException, ClassNotFoundException {
		JCodeModel codeModel = new JCodeModel();
		List<Entity> entities = entityManager.createQuery("from com.blink.designer.model.Entity").getResultList();
		for( Entity entity: entities) {
			generateModel(codeModel,entity,app);
		}

		File srcDirectory = new File(srcLocation); //getTempDirectory();
		codeModel.build(srcDirectory);

		return compileModel(srcDirectory, app.getName());
	}

	protected File compileModel(File srcDirectory, String appName) throws FileNotFoundException, IOException {

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

		Iterable<? extends JavaFileObject> compilationUnits1 = fileManager.getJavaFileObjectsFromFiles(getFilesWithType(srcDirectory,JAVA_SUFFIX));

		File classesDir = new File(srcDirectory,targetClassesLocation);
		classesDir.mkdirs();
		Iterable<String> options = Arrays.asList( new String[] { "-d", classesDir.getAbsolutePath()} );

		compiler.getTask(null, fileManager, null, options, null, compilationUnits1).call();

		File modelJarLocation = new File(srcDirectory,targetPackageLocation );
		modelJarLocation.mkdirs();
		return packageModel(classesDir, modelJarLocation, appName);
	}

	private List<File> getFilesWithType(File dir, String suffix) {
		System.out.println("Finding java in directory :" + dir.getAbsolutePath());
		List<File> files = new ArrayList<>();
		for( File file : dir.listFiles()) {
			if( file.isDirectory() ) 
				files.addAll(getFilesWithType(file,suffix)) ;
			else {
				if( file.getName().endsWith(suffix))
					files.add(file);
			}
		}
		return files;
	}

	protected File packageModel(File classesDirectory, File modelJarLocation, String appName) throws FileNotFoundException, IOException {
		List<File> clazzFiles = getFilesWithType(classesDirectory,".class");

		File jarFile = new File(modelJarLocation + "/"+ appName+ ".jar"); 
		if (jarFile.exists() == true) { 
			jarFile.delete(); 
		} 

		try(JarOutputStream jarout = new JarOutputStream(new FileOutputStream(jarFile));) {

			for(File clazzFile : clazzFiles) {
				
				String relativeClasspath = classesDirectory.toURI().relativize(clazzFile.toURI()).getPath();
				
				JarEntry entry = new JarEntry(relativeClasspath);
				jarout.putNextEntry(entry);

				try (InputStream filereader = new FileInputStream(clazzFile); ) {
					final int buffersize = 1024; 
					byte buffer[] = new byte[buffersize]; 
					int readcount = 0;
					while ((readcount = filereader.read(buffer, 0, buffersize)) >= 0) {
						if (readcount > 0) {
							jarout.write(buffer, 0, readcount); 
						} 
					} 
				}
			}
		} 
		
		return jarFile;
	}

	private void generateModel(JCodeModel codeModel,Entity entity,App app) throws ClassNotFoundException {
		JPackage parentPackage = null;
		if( app.getBasePackage() != null)
			parentPackage =  getPackage(codeModel, app.getBasePackage());
		else 
			parentPackage = getPackage(codeModel, entity.getParentPackage().getName());
		generateModel(codeModel,entity, parentPackage);
	}

	protected void generateModel(JCodeModel codeModel,Entity entity,JPackage parentPackage) throws ClassNotFoundException {

		JDefinedClass entityClass = null;
		try {
			entityClass = parentPackage._class(entity.getName());
		} catch (JClassAlreadyExistsException e) {
			entityClass = parentPackage._getClass(entity.getName());
		}

		generateAttributes(codeModel, entityClass, entity) ;
	}


	protected void generateAttributes(JCodeModel codeModel, JDefinedClass entityClass, Entity entity) throws ClassNotFoundException {
		if(  entity.getEntityAttributes() == null) {
			return; 
		}

		for(EntityAttribute entityAttribute : entity.getEntityAttributes()) {
			entityClass.field(JMod.PRIVATE, getType(codeModel,entityAttribute.getType().getName() ), entityAttribute.getName());
		}
	}


	private JType getType(JCodeModel codeModel, String name ) throws ClassNotFoundException {
		Type type = (Type) entityManager.createQuery("from com.blink.designer.model.Type where name = '" + name+"'").getSingleResult() ;
		return codeModel.parseType(type.getClassName());
	}

	protected JPackage getPackage(JCodeModel codeModel , String packageName) {
		return codeModel._package(packageName);
	}

	private File getTempDirectory() throws IOException {
		final File temp;

		File repoFile = new File(localRepo);
		if( !repoFile.exists() ) 
			repoFile.mkdirs();

		temp = File.createTempFile("blink", Long.toString(System.nanoTime()), repoFile);

		if(!(temp.delete()))
		{
			throw new IOException("Could not delete temp file: " + temp.getAbsolutePath());
		}

		if(!(temp.mkdir()))
		{
			throw new IOException("Could not create temp directory: " + temp.getAbsolutePath());
		}

		return (temp);
	}

	public String getLocalRepo() {
		return localRepo;
	}

	public void setLocalRepo(String localRepo) {
		this.localRepo = localRepo;
	}


}
