node {
	def app

	System.setProperty("org.jenkinsci.plugins.durabletask.BourneShellScript.HEARTBEAT_CHECK_INTERVAL", "86400")

	stage('Clone repository') {
		echo 'Cloning repository...'
		checkout scm
		echo 'Repository cloned'
	}

	stage('Build api image') {
		echo 'Building api image...'
		dir('backend') {
			retry(3) {
				app = docker.build("stock_simulator_api_image:latest")
			}
		}
		echo 'Image built'
	}


	stage('Build web image') {
		echo 'Building web image...'
		dir('frontend') {
			retry(3) {
				app = docker.build("stock_simulator_web_image:latest")
			}
		}
		echo 'Image built'
	}

	stage('Deploying stock_simulator') {
		sh '/home/hera/scripts/restart_composition.sh'
	}
}
