package models

// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object Tables extends {
    val profile = slick.jdbc.MySQLProfile
} with Tables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
    val profile: slick.jdbc.JdbcProfile

    import profile.api._
    import slick.model.ForeignKeyAction
    // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
    import slick.jdbc.{GetResult => GR}

    /** DDL for all tables. Call .create to execute. */
    lazy val schema
    : profile.SchemaDescription = LoginStatuses.schema ++ MatchRelations.schema ++ Messages.schema ++ Users.schema ++ UsersLocation.schema

    @deprecated("Use .schema instead of .ddl", "3.0")
    def ddl = schema

    /** Entity class storing rows of table LoginStatuses
      *
      * @param userId        Database column USER_ID SqlType(INT)
      * @param loginDatetime Database column LOGIN_DATETIME SqlType(DATETIME) */
    final case class LoginStatusesRow(userId: Int, loginDatetime: java.sql.Timestamp)

    /** GetResult implicit for fetching LoginStatusesRow objects using plain SQL queries */
    implicit def GetResultLoginStatusesRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp]): GR[LoginStatusesRow] = GR {
        prs =>
            import prs._
            LoginStatusesRow.tupled((<<[Int], <<[java.sql.Timestamp]))
    }

    /** Table description of table LOGIN_STATUSES. Objects of this class serve as prototypes for rows in queries. */
    class LoginStatuses(_tableTag: Tag) extends profile.api.Table[LoginStatusesRow](_tableTag, "LOGIN_STATUSES") {
        def * = (userId, loginDatetime) <> (LoginStatusesRow.tupled, LoginStatusesRow.unapply)

        /** Maps whole row to an option. Useful for outer joins. */
        def ? =
            (Rep.Some(userId), Rep.Some(loginDatetime)).shaped.<>({ r =>
                import r._; _1.map(_ => LoginStatusesRow.tupled((_1.get, _2.get)))
            }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

        /** Database column USER_ID SqlType(INT) */
        val userId: Rep[Int] = column[Int]("USER_ID")

        /** Database column LOGIN_DATETIME SqlType(DATETIME) */
        val loginDatetime: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("LOGIN_DATETIME")

        /** Foreign key referencing Users (database name FK_LOGIN_STATUS_USERS) */
        lazy val usersFk = foreignKey("FK_LOGIN_STATUS_USERS", userId, Users)(
            r => r.userId,
            onUpdate = ForeignKeyAction.NoAction,
            onDelete = ForeignKeyAction.NoAction
        )
    }

    /** Collection-like TableQuery object for table LoginStatuses */
    lazy val LoginStatuses = new TableQuery(tag => new LoginStatuses(tag))

    /** Entity class storing rows of table MatchRelations
      *
      * @param userId           Database column USER_ID SqlType(INT)
      * @param partnerId        Database column PARTNER_ID SqlType(INT)
      * @param matchState       Database column MATCH_STATE SqlType(INT)
      * @param selectedDatetime Database column SELECTED_DATETIME SqlType(DATETIME) */
    final case class MatchRelationsRow(userId: Int, partnerId: Int, matchState: Int, selectedDatetime: java.sql.Timestamp)

    /** GetResult implicit for fetching MatchRelationsRow objects using plain SQL queries */
    implicit def GetResultMatchRelationsRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp]): GR[MatchRelationsRow] =
        GR { prs =>
            import prs._
            MatchRelationsRow.tupled((<<[Int], <<[Int], <<[Int], <<[java.sql.Timestamp]))
        }

    /** Table description of table MATCH_RELATIONS. Objects of this class serve as prototypes for rows in queries. */
    class MatchRelations(_tableTag: Tag) extends profile.api.Table[MatchRelationsRow](_tableTag, "MATCH_RELATIONS") {
        def * = (userId, partnerId, matchState, selectedDatetime) <> (MatchRelationsRow.tupled, MatchRelationsRow.unapply)

        /** Maps whole row to an option. Useful for outer joins. */
        def ? =
            (Rep.Some(userId), Rep.Some(partnerId), Rep.Some(matchState), Rep.Some(selectedDatetime)).shaped.<>({ r =>
                import r._; _1.map(_ => MatchRelationsRow.tupled((_1.get, _2.get, _3.get, _4.get)))
            }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

        /** Database column USER_ID SqlType(INT) */
        val userId: Rep[Int] = column[Int]("USER_ID")

        /** Database column PARTNER_ID SqlType(INT) */
        val partnerId: Rep[Int] = column[Int]("PARTNER_ID")

        /** Database column MATCH_STATE SqlType(INT) */
        val matchState: Rep[Int] = column[Int]("MATCH_STATE")

        /** Database column SELECTED_DATETIME SqlType(DATETIME) */
        val selectedDatetime: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("SELECTED_DATETIME")

        /** Foreign key referencing Users (database name FK_MATCH_USERS) */
        lazy val usersFk = foreignKey("FK_MATCH_USERS", userId, Users)(
            r => r.userId,
            onUpdate = ForeignKeyAction.NoAction,
            onDelete = ForeignKeyAction.NoAction
        )

        /** Uniqueness Index over (userId,partnerId) (database name UQ_match_relations) */
        val index1 = index("UQ_match_relations", (userId, partnerId), unique = true)
    }

    /** Collection-like TableQuery object for table MatchRelations */
    lazy val MatchRelations = new TableQuery(tag => new MatchRelations(tag))

    /** Entity class storing rows of table Messages
      *
      * @param userId       Database column USER_ID SqlType(INT)
      * @param partnerId    Database column PARTNER_ID SqlType(INT)
      * @param message      Database column MESSAGE SqlType(TEXT), Length(65535,true)
      * @param sendDatetime Database column SEND_DATETIME SqlType(DATETIME) */
    final case class MessagesRow(userId: Int, partnerId: Int, message: String, sendDatetime: java.sql.Timestamp)

    /** GetResult implicit for fetching MessagesRow objects using plain SQL queries */
    implicit def GetResultMessagesRow(implicit e0: GR[Int], e1: GR[String], e2: GR[java.sql.Timestamp]): GR[MessagesRow] =
        GR { prs =>
            import prs._
            MessagesRow.tupled((<<[Int], <<[Int], <<[String], <<[java.sql.Timestamp]))
        }

    /** Table description of table MESSAGES. Objects of this class serve as prototypes for rows in queries. */
    class Messages(_tableTag: Tag) extends profile.api.Table[MessagesRow](_tableTag, "MESSAGES") {
        def * = (userId, partnerId, message, sendDatetime) <> (MessagesRow.tupled, MessagesRow.unapply)

        /** Maps whole row to an option. Useful for outer joins. */
        def ? =
            (Rep.Some(userId), Rep.Some(partnerId), Rep.Some(message), Rep.Some(sendDatetime)).shaped.<>({ r =>
                import r._; _1.map(_ => MessagesRow.tupled((_1.get, _2.get, _3.get, _4.get)))
            }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

        /** Database column USER_ID SqlType(INT) */
        val userId: Rep[Int] = column[Int]("USER_ID")

        /** Database column PARTNER_ID SqlType(INT) */
        val partnerId: Rep[Int] = column[Int]("PARTNER_ID")

        /** Database column MESSAGE SqlType(TEXT), Length(65535,true) */
        val message: Rep[String] = column[String]("MESSAGE", O.Length(65535, varying = true))

        /** Database column SEND_DATETIME SqlType(DATETIME) */
        val sendDatetime: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("SEND_DATETIME")

        /** Foreign key referencing Users (database name FK_MESSAGES_USERS) */
        lazy val usersFk = foreignKey("FK_MESSAGES_USERS", userId, Users)(
            r => r.userId,
            onUpdate = ForeignKeyAction.NoAction,
            onDelete = ForeignKeyAction.NoAction
        )
    }

    /** Collection-like TableQuery object for table Messages */
    lazy val Messages = new TableQuery(tag => new Messages(tag))

    /** Entity class storing rows of table Users
      *
      * @param userId       Database column USER_ID SqlType(INT), AutoInc, PrimaryKey
      * @param userName     Database column USER_NAME SqlType(VARCHAR), Length(30,true)
      * @param sex          Database column SEX SqlType(INT)
      * @param birthday     Database column BIRTHDAY SqlType(DATE)
      * @param profile      Database column PROFILE SqlType(TEXT), Length(65535,true)
      * @param createdAt    Database column CREATED_AT SqlType(DATE)
      * @param mailAddress  Database column MAIL_ADDRESS SqlType(VARCHAR), Length(255,true)
      * @param password     Database column PASSWORD SqlType(VARCHAR), Length(255,true)
      * @param profileImage Database column PROFILE_IMAGE SqlType(VARCHAR), Length(256,true) */
    final case class UsersRow(userId: Option[Int] = None,
                              userName: String,
                              sex: Int,
                              birthday: java.sql.Date,
                              profile: Option[String],
                              createdAt: java.sql.Date,
                              mailAddress: String,
                              password: String,
                              profileImage: String)

    /** GetResult implicit for fetching UsersRow objects using plain SQL queries */
    implicit def GetResultUsersRow(implicit e0: GR[Option[Int]],
                                   e1: GR[String],
                                   e2: GR[Int],
                                   e3: GR[java.sql.Date],
                                   e4: GR[Option[String]]): GR[UsersRow] = GR { prs =>
        import prs._
        UsersRow.tupled(
            (
                <<?[Int],
                <<[String],
                <<[Int],
                <<[java.sql.Date],
                <<?[String],
                <<[java.sql.Date],
                <<[String],
                <<[String],
                <<[String]
            )
        )
    }

    /** Table description of table USERS. Objects of this class serve as prototypes for rows in queries. */
    class Users(_tableTag: Tag) extends profile.api.Table[UsersRow](_tableTag, "USERS") {
        def * =
            (Rep.Some(userId), userName, sex, birthday, profile, createdAt, mailAddress, password, profileImage) <> (UsersRow.tupled, UsersRow.unapply)

        /** Maps whole row to an option. Useful for outer joins. */
        def ? =
            (
                Rep.Some(userId),
                Rep.Some(userName),
                Rep.Some(sex),
                Rep.Some(birthday),
                profile,
                Rep.Some(createdAt),
                Rep.Some(mailAddress),
                Rep.Some(password),
                Rep.Some(profileImage)
            ).shaped.<>({ r =>
                import r._; _1.map(_ => UsersRow.tupled((_1, _2.get, _3.get, _4.get, _5, _6.get, _7.get, _8.get, _9.get)))
            }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

        /** Database column USER_ID SqlType(INT), AutoInc, PrimaryKey */
        val userId: Rep[Int] = column[Int]("USER_ID", O.AutoInc, O.PrimaryKey)

        /** Database column USER_NAME SqlType(VARCHAR), Length(30,true) */
        val userName: Rep[String] = column[String]("USER_NAME", O.Length(30, varying = true))

        /** Database column SEX SqlType(INT) */
        val sex: Rep[Int] = column[Int]("SEX")

        /** Database column BIRTHDAY SqlType(DATE) */
        val birthday: Rep[java.sql.Date] = column[java.sql.Date]("BIRTHDAY")

        /** Database column PROFILE SqlType(TEXT), Length(65535,true) */
        val profile: Rep[Option[String]] = column[Option[String]]("PROFILE", O.Length(65535, varying = true))

        /** Database column CREATED_AT SqlType(DATE) */
        val createdAt: Rep[java.sql.Date] = column[java.sql.Date]("CREATED_AT")

        /** Database column MAIL_ADDRESS SqlType(VARCHAR), Length(255,true) */
        val mailAddress: Rep[String] = column[String]("MAIL_ADDRESS", O.Length(255, varying = true))

        /** Database column PASSWORD SqlType(VARCHAR), Length(255,true) */
        val password: Rep[String] = column[String]("PASSWORD", O.Length(255, varying = true))

        /** Database column PROFILE_IMAGE SqlType(VARCHAR), Length(256,true) */
        val profileImage: Rep[String] = column[String]("PROFILE_IMAGE", O.Length(256, varying = true))

        /** Uniqueness Index over (mailAddress) (database name MAIL_ADDRESS) */
        val index1 = index("MAIL_ADDRESS", mailAddress, unique = true)
    }

    /** Collection-like TableQuery object for table Users */
    lazy val Users = new TableQuery(tag => new Users(tag))

    /** Entity class storing rows of table UsersLocation
      *
      * @param userId    Database column USER_ID SqlType(INT)
      * @param latitude  Database column LATITUDE SqlType(DECIMAL)
      * @param longitude Database column LONGITUDE SqlType(DECIMAL) */
    final case class UsersLocationRow(userId: Int, latitude: scala.math.BigDecimal, longitude: scala.math.BigDecimal)

    /** GetResult implicit for fetching UsersLocationRow objects using plain SQL queries */
    implicit def GetResultUsersLocationRow(implicit e0: GR[Int], e1: GR[scala.math.BigDecimal]): GR[UsersLocationRow] =
        GR { prs =>
            import prs._
            UsersLocationRow.tupled((<<[Int], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal]))
        }

    /** Table description of table USERS_LOCATION. Objects of this class serve as prototypes for rows in queries. */
    class UsersLocation(_tableTag: Tag) extends profile.api.Table[UsersLocationRow](_tableTag, "USERS_LOCATION") {
        def * = (userId, latitude, longitude) <> (UsersLocationRow.tupled, UsersLocationRow.unapply)

        /** Maps whole row to an option. Useful for outer joins. */
        def ? =
            (Rep.Some(userId), Rep.Some(latitude), Rep.Some(longitude)).shaped.<>({ r =>
                import r._; _1.map(_ => UsersLocationRow.tupled((_1.get, _2.get, _3.get)))
            }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

        /** Database column USER_ID SqlType(INT) */
        val userId: Rep[Int] = column[Int]("USER_ID")

        /** Database column LATITUDE SqlType(DECIMAL) */
        val latitude: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("LATITUDE")

        /** Database column LONGITUDE SqlType(DECIMAL) */
        val longitude: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("LONGITUDE")

        /** Foreign key referencing Users (database name FK_USER_LOCATION_USERS) */
        lazy val usersFk = foreignKey("FK_USER_LOCATION_USERS", userId, Users)(
            r => r.userId,
            onUpdate = ForeignKeyAction.NoAction,
            onDelete = ForeignKeyAction.NoAction
        )
    }

    /** Collection-like TableQuery object for table UsersLocation */
    lazy val UsersLocation = new TableQuery(tag => new UsersLocation(tag))
}
