package com.adobe.phonegap.push;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;

public class Invites {

    private int recurrenceCount, interval = 1;
    public String frequency;
    public Long reminder = null, until = null;
    public Boolean isRecurrence;
    private Calendar startDate;
    private String startTime;
    private String[] byDayList = new String[0], byMonthDayList, byMonthList;
    private Boolean isMaxInstance = false;
    private int currentInstance =0;
    private ArrayList<JSONObject> alarmArray = new ArrayList<JSONObject>();

    private int dayOfMonth = -1;
    private String dayOfWeek = null;
    private int weekOfMonth = -1;
    private int monthOfYear = -1;

    private Boolean isCustomRecurrence = false;
    private Boolean isException = false;
    private HashMap<String, JSONObject> inviteExceptions = new HashMap<String, JSONObject>();
    private Long reminderBefore = null;
    private String appointmentDetailID = "";

    private DateFormat dateFormat = new SimpleDateFormat("dd-MM-YYYY");

    public Invites(JSONObject eventData, JSONObject invitee, JSONArray exceptions) throws JSONException {

        startTime = invitee.getJSONObject("startTime").get("timestamp").toString();
        isRecurrence = Boolean.valueOf(invitee.getString("isRecurrence"));

        if(invitee.getJSONArray("alarms").length() > 0) {
            reminder = convertToLong(invitee.getJSONArray("alarms").getJSONObject(0).getString("trigger"));
            reminderBefore = (reminder * 1000 * 60);
        }

        appointmentDetailID = invitee.getString("calItemId") + "-" + invitee.getString("msgId");

        if (isRecurrence) {
            JSONObject recurrenceObj = invitee.getJSONArray("recurrence").getJSONObject(0);
            recurrenceCount = convertToInt(recurrenceObj.getString("count"), 0);
            frequency = recurrenceObj.getString("frequency");

            if(recurrenceObj.has("until"))
                until = convertToLong(recurrenceObj.getString("until"));

            interval = convertToInt(recurrenceObj.getString("interval"), 1);

            byDayList = new String[recurrenceObj.getJSONArray("byDayList").length()];

            if (frequency.equals("WEEKLY")) {
                for (int j = 0; j < byDayList.length; j++) {
                    // example ["TH", "FR", "SA"]
                    byDayList[j] = recurrenceObj.getJSONArray("byDayList").getJSONObject(j).getString("day");
                }
            }

            if ((frequency.equals("YEARLY") || frequency.equals("MONTHLY")) && byDayList.length > 0) {
                // example weekOfMonth = 1; ( second week of month)
                // example dayOfWeek = "TH"; (Thursday)
                weekOfMonth = recurrenceObj.getJSONArray("byDayList").getJSONObject(0).getInt("orderWeek");
                dayOfWeek = recurrenceObj.getJSONArray("byDayList").getJSONObject(0).getString("day");
            }

            byMonthDayList = new String[recurrenceObj.getJSONArray("byMonthDayList").length()];

            if(byMonthDayList.length > 0) {
                // example dayOfMonth = 6; (6th day of month)
                dayOfMonth = Integer.parseInt(recurrenceObj.getJSONArray("byMonthDayList").getString(0));
            }

            byMonthList = new String[recurrenceObj.getJSONArray("byMonthList").length()];

            if(byMonthList.length > 0) {
                // example monthOfYear = 7; (July)
                monthOfYear = Integer.parseInt(recurrenceObj.getJSONArray("byMonthList").getString(0));
            }

        }

        if (byDayList.length > 0 || weekOfMonth >=0 || dayOfMonth >=0 || monthOfYear >=0 || interval > 1 || recurrenceCount > 0) {
            isCustomRecurrence = true;
        }

        Date date = new Date(Long.parseLong(startTime));
        startDate = Calendar.getInstance();
        startDate.setTime(date);

        if(exceptions.length() > 0) {
            isException = true;
            for(int i=0; i< exceptions.length(); i++) {
                String startTime = exceptions.getJSONObject(i).getJSONObject("startTime").getString("timestamp");
                String status = exceptions.getJSONObject(i).getString("status");
                String appointmentEventDetailId = exceptions.getJSONObject(i).getString("calItemId") + "-" + exceptions.getJSONObject(i).getString("msgId");

                Date dt = new Date(Long.parseLong(startTime));
                String exceptionDate = dateFormat.format(dt);
                JSONObject exceptionDetails = new JSONObject();
                exceptionDetails.put("startTime", startTime);
                exceptionDetails.put("status", status);
                exceptionDetails.put("appointmentDetailID", appointmentEventDetailId);
                inviteExceptions.put(exceptionDate, exceptionDetails);
            }
        }
    }


    public ArrayList<JSONObject> alarms () {

        if (reminder != null) {
            if (isCustomRecurrence) {
                if (frequency.equals("WEEKLY")) {
                    getWeeklyCustomAlarm();
                } else if (frequency.equals("MONTHLY")) {
                    getMonthlyCustomAlarm();
                } else if (frequency.equals("YEARLY")) {
                    getYearlyCustomAlarm();
                } else {
                    getDailyCustomAlarm();
                }
            } else if (isRecurrence) {
                getDefaultRecurrenceAlarm();
            } else {
                getEventAlarm();
            }
        }

        return alarmArray;
    }


    private void getWeeklyCustomAlarm() {

        int nextWeek = 0;
        /***
         *
         * "recurrence": [
         *                 {
         *                     "count": 0,
         *                     "frequency": "WEEKLY",
         *                     "until": 1944424800000,
         *                     "byDayList": [
         *                         "TU”, “WE”
         *                     ],
         *                     "byHourList": [],
         *                     "byMinuteList": [],
         *                     "byMonthDayList": [],
         *                     "byMonthList": [],
         *                     "byBySecondList": [],
         *                     "bySetPosList": [],
         *                     "byWeekNoList": [],
         *                     "byYearDayList": []
         *                 }
         *             ],
         */

        while(!isMaxInstance) {
            for (int i = 0; i < byDayList.length; i++) {
                Calendar calendar = (Calendar) startDate.clone();
                int weekday = calendar.get(Calendar.DAY_OF_WEEK);
                int days = ((Calendar.SATURDAY - weekday + getWeekDay(byDayList[i])) % 7) + (7 * nextWeek);
                calendar.add(Calendar.DAY_OF_YEAR, days);
                Long nextAlarm = calendar.getTimeInMillis();

                if (pushAlarm(nextAlarm))
                    break;
            }
            nextWeek += interval;
        }
    }

    private void getMonthlyCustomAlarm() {
        int nextMonth = 0;
        while(!isMaxInstance) {

            Calendar calendar = (Calendar) startDate.clone();
            if(dayOfMonth >= 0) {
                /**
                 *
                 * "recurrence": [
                 *                 {
                 *                     "bySetPosList": [],
                 *                     "byMonthDayList":
                 * 			[ 7 ],
                 *                     "bySecondList": [],
                 *                     "count": 0,
                 *                     "byMinuteList": [],
                 *                     "byYearDayList": [],
                 *                     "frequency": "MONTHLY",
                 *                     "byWeekNoList": [],
                 *                     "byHourList": [],
                 *                     "byDayList": [],
                 *                     "interval": 1,
                 *                     "until": 2575000800000,
                 *                     "byMonthList": []
                 *                 }
                 *             ],
                 */
                calendar.add(Calendar.MONTH, nextMonth);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            } else {
                /**
                 *
                 * "recurrence": [
                 *                 {
                 *                     "bySetPosList": [],
                 *                     "byMonthDayList": [],
                 *                     "bySecondList": [],
                 *                     "count": 0,
                 *                     "byMinuteList": [],
                 *                     "byYearDayList": [],
                 *                     "frequency": "MONTHLY",
                 *                     "byWeekNoList": [],
                 *                     "byHourList": [],
                 *                     "byDayList": [
                 *                         {
                 *                             "orderWeek": 1,
                 *                             "day": "TU"
                 *                         }
                 *                     ],
                 *                     "interval": 1,
                 *                     "until": 2575000800000,
                 *                     "byMonthList": []
                 *                 }
                 *             ],
                 */
                calendar.add(Calendar.MONTH, nextMonth);;
                calendar.set(Calendar.DAY_OF_WEEK, getWeekDay(dayOfWeek));
                calendar.set(Calendar.DAY_OF_WEEK_IN_MONTH, weekOfMonth + 1);
            }
            Long nextAlarm = calendar.getTimeInMillis();
            pushAlarm(nextAlarm);
            nextMonth += interval;
        }
    }

    private void getYearlyCustomAlarm() {
        int nextYear = 0;
        while(!isMaxInstance) {


            Calendar calendar = (Calendar) startDate.clone();
            if(monthOfYear >= 0 && weekOfMonth >= 0 && dayOfWeek != null) {
                /***
                 *
                 * "recurrence": [
                 *                 {
                 *                     "bySetPosList": [
                 *                         1
                 *                     ],
                 *                     "byMonthDayList": [],
                 *                     "bySecondList": [],
                 *                     "count": 0,
                 *                     "byMinuteList": [],
                 *                     "byYearDayList": [],
                 *                     "frequency": "YEARLY",
                 *                     "byWeekNoList": [],
                 *                     "byHourList": [],
                 *                     "byDayList": [
                 *                         {
                 *                             "orderWeek": 0,
                 *                             "day": "TU"
                 *                         }
                 *                     ],
                 *                     "interval": 1,
                 *                     "until": 4755133800000,
                 *                     "byMonthList": [
                 *                         9
                 *                     ]
                 *                 }
                 *             ],
                 */
                calendar.add(Calendar.YEAR, nextYear);
                calendar.set(Calendar.MONTH, monthOfYear - 1);
                calendar.set(Calendar.DAY_OF_WEEK, getWeekDay(dayOfWeek));
                calendar.set(Calendar.DAY_OF_WEEK_IN_MONTH, weekOfMonth + 1);

            } else {
                /***
                 *
                 * "recurrence": [
                 *                 {
                 *                     "bySetPosList": [],
                 *                     "byMonthDayList": [
                 *                         7
                 *                     ],
                 *                     "bySecondList": [],
                 *                     "count": 0,
                 *                     "byMinuteList": [],
                 *                     "byYearDayList": [],
                 *                     "frequency": "YEARLY",
                 *                     "byWeekNoList": [],
                 *                     "byHourList": [],
                 *                     "byDayList": [],
                 *                     "interval": 1,
                 *                     "until": 4755133800000,
                 *                     "byMonthList": [
                 *                         9
                 *                     ]
                 *                 }
                 *             ],
                 */

                calendar.add(Calendar.YEAR, nextYear);
                calendar.set(Calendar.MONTH, monthOfYear - 1);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            }
            Long nextAlarm = calendar.getTimeInMillis();
            pushAlarm(nextAlarm);
            nextYear += interval;
        }
    }

    private void getDailyCustomAlarm() {
        int nextDay = 0;
        while(!isMaxInstance) {

            Calendar calendar = (Calendar) startDate.clone();
            calendar.add(Calendar.DATE, nextDay);
            Long nextAlarm = calendar.getTimeInMillis();
            pushAlarm(nextAlarm);
            nextDay += interval;
        }
    }


    private void getDefaultRecurrenceAlarm() {

        /***
         *
         * "invites": [
         *         {
         *             "calItemId": 1185,
         *             "calItemFolderId": 10,
         *             "fragment": "",
         *             "classProp": "PUB",
         *             "desc": "\n",
         *             "isOrganizer": true,
         *             "location": "",
         *             "name": "test",
         *             "organizer": {
         *                 "name": "test",
         *                 "email": "user1@zulip1.com"
         *             },
         *             "attendees": [],
         *             "uid": "c19ba60f-7cad-4e81-9d53-6f4340672fe4",
         *             "url": "",
         *             "rvsp": true,
         *             "isAllDayEvent": false,
         *             "status": "CONF",
         *             "isRecurrence": false,
         *             "recurrence": [],
         *             "alarms": [
         *                 {
         *                     "action": "DISPLAY",
         *                     "trigger": -5,
         *                     "repeatCount": 0
         *                 }
         *             ],
         *             "startTime": {
         *                 "timestamp": 1630571400000,
         *                 "utcTime": "20210902T083000Z",
         *                 "timezone": "Asia/Kolkata"
         *             },
         *             "endTime": {
         *                 "timestamp": 1630575000000,
         *                 "utcTime": "20210902T093000Z",
         *                 "timezone": "Asia/Kolkata"
         *             }
         *         }
         *     ]
         */

        int nextInterval = 0;
        while(!isMaxInstance) {

            Calendar calendar = (Calendar) startDate.clone();
            calendar = getNextRecurrence(calendar, nextInterval);
            Long nextAlarm = calendar.getTimeInMillis();
            pushAlarm(nextAlarm);
            nextInterval += interval;
        }
    }

    private void getEventAlarm() {
        Calendar calendar = (Calendar) startDate.clone();
        Long nextAlarm = calendar.getTimeInMillis();
        alarmArray.add(alarmAndDetailsId(nextAlarm + reminderBefore, appointmentDetailID));
    }

    private JSONObject alarmAndDetailsId(Long alarmAt, String eventDetailId) {

        JSONObject obj = new JSONObject();
        try {
            obj.put("triggerAt", alarmAt);
            obj.put("eventDetailId", eventDetailId);
        } catch (Exception e) {
            System.out.println(e);
            return  obj;
        }
        return obj;
    }

    private int getWeekDay(String weekday) {

        switch (weekday) {
            case "SU": return Calendar.SUNDAY;
            case "MO": return Calendar.MONDAY;
            case "TU": return Calendar.TUESDAY;
            case "WE": return Calendar.WEDNESDAY;
            case "TH": return Calendar.THURSDAY;
            case "FR": return Calendar.FRIDAY;
            case "SA": return Calendar.SATURDAY;
            default:
                return 0;
        }
    }

    private Boolean pushAlarm(Long nextAlarm) {
        Boolean isBeyondLastInstance = true; // To create single alarm for an event
        if (recurrenceCount > 0) {
            isBeyondLastInstance =  currentInstance > recurrenceCount - 1;
        } else if(until != null){
            isBeyondLastInstance = nextAlarm > until;
        }

        if(!isBeyondLastInstance) {
            // check that is exception present for current date.
            if(isException && inviteExceptions.size() > 0) {
                Date dt = new Date(nextAlarm);
                String currentDate = dateFormat.format(dt);
                if (inviteExceptions.containsKey(currentDate)) {
                    try {
                        if (inviteExceptions.get(currentDate).getString("status").equals("CONF")) {
                            // Update alarm for current exception and remove it from the list
                            Long alarmAt = Long.parseLong(inviteExceptions.get(currentDate).getString("startTime")) + reminderBefore;
                            alarmArray.add(alarmAndDetailsId(alarmAt, inviteExceptions.get(currentDate).getString("appointmentDetailID")));
                            inviteExceptions.remove(currentDate);
                        } else {
                            // This is cancelled exception. Just remove it from the list and do not add alarm for this day
                            inviteExceptions.remove(currentDate);
                        }
                    } catch (JSONException e) {
                        System.out.println(e.getMessage());
                    }
                } else {
                    alarmArray.add(alarmAndDetailsId(nextAlarm + reminderBefore, appointmentDetailID));
                }
            }else {
                alarmArray.add(alarmAndDetailsId(nextAlarm + reminderBefore, appointmentDetailID));
            }
            currentInstance++;
        } else {
            isMaxInstance = true;
        }

        return isBeyondLastInstance;
    }

    private Calendar getNextRecurrence(Calendar calendar, int nextInterval) {

        switch (frequency) {
            case "WEEKLY" :
                calendar.add(Calendar.DAY_OF_WEEK, nextInterval * 7);
                break;
            case "DAILY":
                calendar.add(Calendar.DAY_OF_WEEK, nextInterval);
                break;
            case "YEARLY":
                calendar.add(calendar.YEAR, nextInterval);
                break;
            case "MONTHLY":
                calendar.add(calendar.MONTH, nextInterval);
                break;
        }
        return calendar;
    }

    private Long convertToLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return null;
        }
    }

    private int convertToInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

}
