import React, {createContext, useState} from 'react'
import Header from './newHeader'
import Footer from './Footer'
import {Content, Theme} from "@carbon/react";
import {NotificationKinds} from "../common/CustomNotification";

export const UserInformationContext = createContext(null);
export const NotificationContext = createContext(null);

export default function Layout(props) {
    const {children} = props
    const [notificationVisible, setNotificationVisible] = useState(false);
    const [notificationBody, setNotificationBody] = useState({
        title: "",
        message: "",
        kind: NotificationKinds.info
    });

    return (
        <>
            <div className="d-flex flex-column min-vh-100">
                <Header user={props.user} config={props.config} onChangeLanguage={props.onChangeLanguage}
                        isLoggedIn={props.isLoggedIn} logout={props.logout}/>
                <Theme theme="white">
                    <Content>
                        <UserInformationContext.Provider value={{user: props.user}}>
                            <NotificationContext.Provider
                                value={{notificationVisible, setNotificationVisible, notificationBody, setNotificationBody}}>
                                {children}
                            </NotificationContext.Provider>
                        </UserInformationContext.Provider>
                    </ Content>
                </Theme>
                <Footer/>
            </div>
        </>
    )
}