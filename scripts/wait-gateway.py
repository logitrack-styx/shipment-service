"""Wait for discovery convergence after a deployment; never hide test assertion failures."""
import time
from sprint3_e2e import ENV, login

stable=0
for attempt in range(60):
    try:
        result=login(ENV.get('ADMIN_USERNAME','admin'),ENV['ADMIN_PASSWORD'])
        if result[0]==200:
            stable+=1
            if stable>=5:
                print('Gateway login route ready: five consecutive successes after discovery convergence')
                break
        else:stable=0
    except OSError:stable=0
    time.sleep(2)
else:raise RuntimeError('Gateway did not route to Security within readiness window')
