# kotlin-walletconnect-lib
library to use WalletConnect with Kotlin or Java
To add the latest release of the library into android project,

Step 1: 
Add it in your root (Project level) build.gradle at the end of repositories:
allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
  
  Step 2: Add the dependency to module level gradle file:
  dependencies {
	        implementation 'com.github.WalletConnect:kotlin-walletconnect-lib:0.9.6'
	}
