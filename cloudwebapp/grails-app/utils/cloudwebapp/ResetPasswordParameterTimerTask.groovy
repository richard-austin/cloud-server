package cloudwebapp
/**
 * ResetPasswordParameterTimerTask: Removes the map entry for the reset password parameter after the timeout period
 */
class ResetPasswordParameterTimerTask extends  TimerTask{
    String email
    Map<String, String> map
    Map<String, Timer> timerMap

    ResetPasswordParameterTimerTask(String email, Map<String, String> map, Map<String, Timer> timerMap)
    {
        this.email = email
        this.map = map
        this.timerMap = timerMap
    }

    @Override
    void run() {
        map.remove(email)
        timerMap.remove(email)
    }
}
