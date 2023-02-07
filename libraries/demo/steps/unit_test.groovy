void call(){
  println "stage name = ${stepContext.name}"
  println "param1 = ${stageContext.args.param1}"
  println "param2 = ${stageContext.args.param2}"
}
